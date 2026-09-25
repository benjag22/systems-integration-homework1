package com.cargasur.dispatch.client.repository;

import com.cargasur.dispatch.client.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Integer> {
    boolean existsByEmail(String email);
}