package com.nashi.repo;

import com.nashi.domain.NewsPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewsPostRepository extends MongoRepository<NewsPost, String> {

    // Отримати всі новини, відсортовані від найновіших до найстаріших
    List<NewsPost> findAllByOrderByCreatedAtDesc();

    Page<NewsPost> findAllByOrderByCreatedAtDesc(Pageable pageable);

}