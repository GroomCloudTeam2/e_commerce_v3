package com.groom.product.review.application.service.intergration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.groom.e_commerce.global.infrastructure.client.OpenAi.OpenAiClient;
import com.groom.e_commerce.product.domain.entity.Category;
import com.groom.e_commerce.product.domain.entity.Product;
import com.groom.e_commerce.product.domain.repository.ProductRepository;
import com.groom.e_commerce.review.application.service.ReviewAiSummaryService;
import com.groom.e_commerce.review.domain.entity.ProductRatingEntity;
import com.groom.e_commerce.review.domain.entity.ReviewCategory;
import com.groom.e_commerce.review.domain.repository.ProductRatingRepository;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import jakarta.transaction.Transactional;

@SpringBootTest
@Tag("integration")
class ReviewAiSummaryServiceIntegrationTest {

	@Autowired
	private ReviewAiSummaryService service;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductRatingRepository productRatingRepository;

	@MockBean
	private OpenAiClient openAiClient;
}
