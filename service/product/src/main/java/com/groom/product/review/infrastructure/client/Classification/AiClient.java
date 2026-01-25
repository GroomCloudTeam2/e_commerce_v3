package com.groom.product.review.infrastructure.client.Classification;

import com.groom.product.review.domain.entity.ReviewCategory;
import com.groom.product.review.infrastructure.client.Classification.dto.AiFeignRequest;
import com.groom.product.review.infrastructure.client.Classification.dto.AiFeignResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

	private final AiFeignClient aiFeignClient;

	/**
	 * 리뷰 코멘트 AI 분류
	 * - confidence < 0.6 → ERR
	 * - 예외 발생 → ERR
	 */
	public ReviewCategory classify(String comment) {
		try {
			AiFeignResponse response =
				aiFeignClient.classify(new AiFeignRequest(comment));

			if (response == null ||
				response.category() == null ||
				response.confidence() < 0.6) {

				log.warn(
					"AI 분류 실패 또는 신뢰도 부족 → ERR, comment={}",
					comment
				);
				return ReviewCategory.ERR;
			}

			return mapToCategory(response.category());

		} catch (Exception e) {
			log.error(
				"AI 분류 중 예외 발생 → ERR, comment={}",
				comment,
				e
			);
			return ReviewCategory.ERR;
		}
	}

	/**
	 * AI 문자열 카테고리 → 도메인 enum 변환
	 */
	private ReviewCategory mapToCategory(String category) {
		return switch (category) {
			case "디자인/외형" -> ReviewCategory.DESIGN;
			case "성능/기능" -> ReviewCategory.PERFORMANCE;
			case "편의성/사용감" -> ReviewCategory.CONVENIENCE;
			case "가격/구성" -> ReviewCategory.PRICE;
			case "품질/내구성" -> ReviewCategory.QUALITY;
			default -> ReviewCategory.ERR;
		};
	}
}
