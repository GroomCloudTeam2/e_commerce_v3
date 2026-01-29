package com.groom.payment.application.service;

import static com.groom.common.presentation.advice.ErrorCode.*;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groom.payment.domain.entity.Payment;
import com.groom.payment.domain.entity.PaymentCancel;
import com.groom.payment.domain.model.PaymentStatus;
import com.groom.payment.domain.repository.PaymentCancelRepository;
import com.groom.payment.domain.repository.PaymentRepository;
import com.groom.payment.event.publisher.PaymentEventPublisher;
import com.groom.payment.infrastructure.executor.TossPaymentExecutor;
import com.groom.payment.infrastructure.feign.TossPaymentsClient.TossCancelRequest;
import com.groom.payment.infrastructure.feign.TossPaymentsClient.TossCancelResponse;
import com.groom.payment.infrastructure.feign.TossPaymentsClient.TossConfirmRequest;
import com.groom.payment.infrastructure.feign.TossPaymentsClient.TossConfirmResponse;
import com.groom.payment.presentation.dto.request.ReqCancelPayment;
import com.groom.payment.presentation.dto.request.ReqConfirmPayment;
import com.groom.payment.presentation.dto.response.ResCancelResult;
import com.groom.payment.presentation.dto.response.ResPayment;
import com.groom.payment.presentation.exception.PaymentException;
import com.groom.payment.presentation.exception.TossApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentCommandService {
	private static final String PG_TOSS = "TOSS";

	private final PaymentRepository paymentRepository;
	private final PaymentCancelRepository paymentCancelRepository;

	// [변경] Client 직접 호출 제거 -> Executor 사용
	private final TossPaymentExecutor tossPaymentExecutor;
	private final PaymentEventPublisher paymentEventPublisher;

	/**
	 * READY 생성
	 */
	@Transactional
	public void createReady(UUID orderId, Long amount) {
		paymentRepository.findByOrderId(orderId)
				.ifPresentOrElse(
						existing -> {
							// 멱등: 이미 있으면 아무것도 안 함
						},
						() -> paymentRepository.save(Payment.ready(orderId, amount, PG_TOSS)));
	}

	/**
	 * 결제 승인(confirm)
	 */
	@Transactional
	public ResPayment confirm(ReqConfirmPayment req) {
		UUID orderId = req.orderId();

		Payment payment = paymentRepository.findByOrderId(orderId)
				.orElseThrow(() -> new PaymentException(PAYMENT_NOT_FOUND, "Payment not found. orderId=" + orderId));

		if (payment.getStatus() == PaymentStatus.PAID)
			return ResPayment.from(payment);
		if (payment.getStatus() == PaymentStatus.CANCELLED)
			throw new PaymentException(PAYMENT_ALREADY_CANCELLED, "Payment already cancelled.");
		if (payment.getStatus() == PaymentStatus.FAILED)
			throw new PaymentException(PAYMENT_ALREADY_FAILED, "Payment already failed.");
		if (payment.getStatus() != PaymentStatus.READY)
			throw new PaymentException(PAYMENT_NOT_CONFIRMABLE,
					"Payment not confirmable. status=" + payment.getStatus());

		Long amount = payment.getAmount();

		TossConfirmResponse tossRes;
		try {
			// Executor를 통해 호출 (AOP 적용됨)
			tossRes = tossPaymentExecutor.executeConfirm(
					new TossConfirmRequest(req.paymentKey(), orderId.toString(), amount));

		} catch (TossApiException e) {
			// Toss가 명시적으로 4xx, 5xx 에러 응답을 준 경우 (Business Exception)
			String failCode = e.getErrorCode().getCode();
			String failMessage = e.getErrorCode().getMessage();

			payment.markFailed(failCode, failMessage);
			paymentRepository.save(payment);

			paymentEventPublisher.publishPaymentFailed(orderId, req.paymentKey(), amount, failCode, failMessage);
			throw e;

		} catch (PaymentException e) {
			// Executor의 Fallback에서 던진 PAYMENT_GATEWAY_UNAVAILABLE 등
			throw e;

		} catch (Exception e) {
			// 그 외 예상치 못한 런타임 에러
			String msg = (e.getMessage() != null) ? e.getMessage() : e.getClass().getSimpleName();

			payment.markFailed("TOSS_CONFIRM_UNKNOWN", msg);
			paymentRepository.save(payment);

			paymentEventPublisher.publishPaymentFailed(orderId, req.paymentKey(), amount, "TOSS_CONFIRM_UNKNOWN", msg);

			throw new PaymentException(PAYMENT_CONFIRM_ERROR, "Confirm failed (unknown). " + msg);
		}

		String tossStatus = safe(tossRes.status());

		if ("DONE".equalsIgnoreCase(tossStatus)) {
			payment.markPaid(req.paymentKey(), amount);
			paymentRepository.save(payment);

			paymentEventPublisher.publishPaymentCompleted(orderId, req.paymentKey(), amount);
			return ResPayment.from(payment);
		}

		payment.markFailed("TOSS_NOT_DONE", "Toss status=" + tossStatus);
		paymentRepository.save(payment);

		paymentEventPublisher.publishPaymentFailed(orderId, req.paymentKey(), amount, "TOSS_NOT_DONE",
				"status=" + tossStatus);
		throw new PaymentException(PAYMENT_NOT_DONE, "Toss payment status is not DONE. status=" + tossStatus);
	}

	/**
	 * 결제 취소(환불)
	 */
	@Transactional
	public ResCancelResult cancel(ReqCancelPayment req) {
		UUID orderId = req.orderId();

		Payment payment = paymentRepository.findByOrderId(orderId)
				.orElseThrow(() -> new PaymentException(PAYMENT_NOT_FOUND, "Payment not found. orderId=" + orderId));

		if (payment.getStatus() == PaymentStatus.CANCELLED) {
			return ResCancelResult.from(payment, true, "ALREADY_CANCELLED");
		}

		if (payment.getStatus() != PaymentStatus.PAID) {
			return ResCancelResult.from(payment, false, "NOT_REFUNDABLE_STATUS=" + payment.getStatus());
		}

		String paymentKey = payment.getPaymentKey();
		if (paymentKey == null || paymentKey.isBlank()) {
			throw new PaymentException(PAYMENT_KEY_MISSING, "paymentKey missing for PAID payment.");
		}

		Long cancelAmount = payment.getAmount();
		String cancelReason = (req.cancelReason() == null || req.cancelReason().isBlank())
				? "ORDER_CANCELLED"
				: req.cancelReason();

		TossCancelResponse tossRes;
		try {
			// [변경] Executor를 통해 호출 (AOP 적용됨)
			tossRes = tossPaymentExecutor.executeCancel(
					paymentKey,
					new TossCancelRequest(cancelAmount, cancelReason));
		} catch (TossApiException e) {
			String failCode = e.getErrorCode().getCode();
			String failMessage = e.getErrorCode().getMessage();

			payment.markRefundFailed(failCode, failMessage);
			paymentRepository.save(payment);

			paymentEventPublisher.publishRefundFailed(orderId, paymentKey, cancelAmount, failCode, failMessage);
			return ResCancelResult.from(payment, false, "REFUND_FAILED:" + failCode);
		} catch (Exception e) {
			// Executor Fallback 예외(PaymentException)도 여기서 잡혀서 처리됨
			String failCode = "TOSS_CANCEL_UNKNOWN";
			String failMessage = (e.getMessage() == null) ? "unknown" : e.getMessage();

			payment.markRefundFailed(failCode, failMessage);
			paymentRepository.save(payment);

			paymentEventPublisher.publishRefundFailed(orderId, paymentKey, cancelAmount, failCode, failMessage);
			return ResCancelResult.from(payment, false, "REFUND_FAILED:" + failCode);
		}

		String tossStatus = safe(tossRes.status());

		if ("CANCELED".equalsIgnoreCase(tossStatus) || "PARTIAL_CANCELED".equalsIgnoreCase(tossStatus)) {
			PaymentCancel cancel = PaymentCancel.of(
					payment.getPaymentId(),
					paymentKey,
					cancelAmount,
					LocalDateTime.now());
			paymentCancelRepository.save(cancel);

			payment.markCancelled();
			paymentRepository.save(payment);

			paymentEventPublisher.publishRefundSucceeded(orderId, paymentKey, cancelAmount);
			return ResCancelResult.from(payment, true, "REFUND_SUCCEEDED");
		}

		String failCode = "TOSS_CANCEL_NOT_CANCELED";
		String failMessage = "Toss status=" + tossStatus;

		payment.markRefundFailed(failCode, failMessage);
		paymentRepository.save(payment);

		paymentEventPublisher.publishRefundFailed(orderId, paymentKey, cancelAmount, failCode, failMessage);
		return ResCancelResult.from(payment, false, "REFUND_FAILED:" + failCode);
	}

	private String safe(String v) {
		return v == null ? "" : v;
	}

	// [삭제] 기존 내부 @CircuitBreaker 메소드 및 Fallback 메소드 제거됨
}
