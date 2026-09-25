package com.cargasur.dispatch.client.service;

import com.cargasur.dispatch.client.dto.ClientResponse;
import com.cargasur.dispatch.client.dto.CreateClientRequest;
import com.cargasur.dispatch.client.model.Client;
import com.cargasur.dispatch.client.repository.ClientRepository;
import com.cargasur.dispatch.config.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;

    @Transactional
    public ClientResponse createClient(CreateClientRequest request) {
        if (clientRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("El email ya se encuentra registrado");
        }
        Client client = Client.builder()
                .name(request.name().trim())
                .email(request.email().trim())
                .phone(request.phone() != null ? request.phone().trim() : null)
                .build();

        Client saved = clientRepository.save(client);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public ClientResponse getClientById(Integer id) {
        return clientRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con id: " + id));
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> getAllClients() {
        return clientRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    private ClientResponse mapToResponse(Client client) {
        return new ClientResponse(client.getId(), client.getName(), client.getEmail(), client.getPhone());
    }
}