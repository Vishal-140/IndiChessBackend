package com.example.IndiChessBackend.repo;

import com.example.IndiChessBackend.model.Move;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MoveRepo extends JpaRepository<Move, Long> {
}
