package com.groom.product.product.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.groom.product.product.domain.entity.Product;
import com.groom.product.product.domain.enums.ProductStatus;

import jakarta.persistence.LockModeType;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

	// 재고 차감을 위한 비관적 락 조회
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM Product p WHERE p.id = :id")
	Optional<Product> findByIdWithLock(@Param("id") UUID id);

	// 상품 목록 조회 (메인 페이지용)
	@Query("SELECT p FROM Product p WHERE p.status = 'ON_SALE' AND p.deletedAt IS NULL")
	Page<Product> findAllOnSale(Pageable pageable);

	// 특정 카테고리의 상품 목록 조회
	@Query("SELECT p FROM Product p WHERE p.category.id = :categoryId AND p.status = 'ON_SALE' AND p.deletedAt IS NULL")
	Page<Product> findByCategoryIdAndOnSale(@Param("categoryId") UUID categoryId, Pageable pageable);

	// owner가 자신의 상품 목록을 조회
	@Query("SELECT p FROM Product p WHERE p.ownerId = :ownerId AND p.deletedAt IS NULL")
	Page<Product> findByOwnerId(@Param("ownerId") UUID ownerId, Pageable pageable);

	// 상품 상세 조회(manager/owner)
	@Query("SELECT p FROM Product p WHERE p.id = :id AND p.deletedAt IS NULL")
	Optional<Product> findByIdAndNotDeleted(@Param("id") UUID id);

	// 상품 상세 조회(buyer)
	@Query("SELECT p FROM Product p WHERE p.id = :id AND p.status = 'ON_SALE' AND p.deletedAt IS NULL")
	Optional<Product> findByIdAndOnSale(@Param("id") UUID id);

	// 상품 + 옵션 + 옵션값 한방 조회
	@Query("SELECT p FROM Product p LEFT JOIN FETCH p.options o LEFT JOIN FETCH o.optionValues "
			+ "WHERE p.id = :id AND p.deletedAt IS NULL")
	Optional<Product> findByIdWithOptions(@Param("id") UUID id);

	// 상품 + variant 한방 조회
	@Query("SELECT p FROM Product p LEFT JOIN FETCH p.variants WHERE p.id = :id AND p.deletedAt IS NULL")
	Optional<Product> findByIdWithVariants(@Param("id") UUID id);

	// 상품 상세 조회용 - Step 1: 상품 + 카테고리
	@Query("SELECT p FROM Product p "
			+ "LEFT JOIN FETCH p.category c "
			+ "LEFT JOIN FETCH c.parent "
			+ "WHERE p.id = :id AND p.deletedAt IS NULL")
	Optional<Product> findByIdWithCategory(@Param("id") UUID id);

	// 상품 상세 조회용 - Step 2: 옵션만 조회
	@Query("SELECT DISTINCT p FROM Product p "
			+ "LEFT JOIN FETCH p.options "
			+ "WHERE p.id = :id")
	Optional<Product> findByIdWithOptionsOnly(@Param("id") UUID id);

	// 상품 상세 조회용 - Step 3: variants
	@Query("SELECT p FROM Product p LEFT JOIN FETCH p.variants WHERE p.id = :id")
	Optional<Product> findByIdWithVariantsOnly(@Param("id") UUID id);

	// manager용 상태별 조회
	Page<Product> findByStatusAndDeletedAtIsNull(ProductStatus status, Pageable pageable);

	// 전체 상품 목록
	@Query("SELECT p FROM Product p WHERE p.deletedAt IS NULL")
	Page<Product> findAllNotDeleted(Pageable pageable);

	// 상품 검색
	@Query("SELECT p FROM Product p WHERE p.title LIKE %:keyword% AND p.status = 'ON_SALE' AND p.deletedAt IS NULL")
	Page<Product> searchByTitle(@Param("keyword") String keyword, Pageable pageable);

	// 장바구니/주문 시 여러 상품 정보를 한 번에 조회할 때 사용 (삭제되지 않은 상품만)
	@Query("SELECT p FROM Product p WHERE p.id IN :ids AND p.deletedAt IS NULL")
	List<Product> findByIdInAndNotDeleted(@Param("ids") List<UUID> ids);

	Optional<String> findTitleById(UUID id);

	boolean existsByCategoryIdAndDeletedAtIsNull(UUID categoryId);

	// 옵션 없는 상품 중 특정 상태인 상품들 조회 (재고 동기화용)
	@Query("SELECT p FROM Product p WHERE p.hasOptions = false AND p.status = :status AND p.deletedAt IS NULL")
	List<Product> findAllByHasOptionsFalseAndStatus(@Param("status") ProductStatus status);

	// 재고 동기화용: 전체 상품 + variant 한 번에 조회
	@Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.variants WHERE p.deletedAt IS NULL")
	List<Product> findAllWithVariants();
}
