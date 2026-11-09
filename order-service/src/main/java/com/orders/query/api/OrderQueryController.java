package com.orders.query.api;

import com.orders.command.domain.OrderNotFoundException;
import com.orders.infrastructure.eventstore.EventStoreRepository;
import com.orders.infrastructure.ownership.OrderAccessService;
import com.orders.query.model.OrderItemView;
import com.orders.query.model.OrderSummary;
import com.orders.query.repository.OrderItemViewRepository;
import com.orders.query.repository.OrderSummaryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderQueryController {
    private static final int MAX_PAGE_SIZE = 100;

    private final OrderSummaryRepository summaryRepo;
    private final OrderItemViewRepository itemRepo;
    private final EventStoreRepository eventStoreRepo;
    private final OrderAccessService orderAccessService;

    public OrderQueryController(OrderSummaryRepository summaryRepo,
                                OrderItemViewRepository itemRepo,
                                EventStoreRepository eventStoreRepo,
                                OrderAccessService orderAccessService) {
        this.summaryRepo = summaryRepo;
        this.itemRepo = itemRepo;
        this.eventStoreRepo = eventStoreRepo;
        this.orderAccessService = orderAccessService;
    }

    @GetMapping
    public Page<OrderSummary> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        Pageable bounded = boundedPageable(pageable);
        return orderAccessService.isAdmin(authentication)
                ? summaryRepo.findAll(bounded)
                : summaryRepo.findByOwnerSubject(authentication.getName(), bounded);
    }

    @GetMapping("/customer")
    public Page<OrderSummary> findByCustomer(@RequestParam String firstName,
                                             @RequestParam String lastName,
                                             @PageableDefault(size = 20, sort = "createdAt",
                                                     direction = Sort.Direction.DESC) Pageable pageable,
                                             Authentication authentication) {
        Pageable bounded = boundedPageable(pageable);
        return orderAccessService.isAdmin(authentication)
                ? summaryRepo.findByFirstNameIgnoreCaseAndLastNameIgnoreCase(firstName, lastName, bounded)
                : summaryRepo.findByOwnerSubjectAndFirstNameIgnoreCaseAndLastNameIgnoreCase(
                        authentication.getName(), firstName, lastName, bounded);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderSummary> get(@PathVariable UUID id, Authentication authentication) {
        orderAccessService.requireAccess(id, authentication);
        return summaryRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @GetMapping("/{id}/items")
    public List<OrderItemView> items(@PathVariable UUID id, Authentication authentication) {
        orderAccessService.requireAccess(id, authentication);
        if (!summaryRepo.existsById(id)) {
            throw new OrderNotFoundException(id);
        }
        return itemRepo.findByOrderId(id);
    }

    @GetMapping("/{id}/events")
    public Slice<EventResponse> events(
            @PathVariable UUID id,
            @PageableDefault(size = 50, sort = "sequenceNumber", direction = Sort.Direction.ASC) Pageable pageable,
            Authentication authentication) {
        orderAccessService.requireAccess(id, authentication);
        if (!eventStoreRepo.existsByAggregateId(id)) {
            throw new OrderNotFoundException(id);
        }
        return eventStoreRepo.findByAggregateIdOrderBySequenceNumberAsc(id, boundedPageable(pageable))
                .map(EventResponse::fromRecord);
    }

    private Pageable boundedPageable(Pageable pageable) {
        if (pageable.isUnpaged()) {
            return PageRequest.of(0, MAX_PAGE_SIZE, pageable.getSort());
        }
        int pageSize = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        return PageRequest.of(pageable.getPageNumber(), pageSize, pageable.getSort());
    }
}
