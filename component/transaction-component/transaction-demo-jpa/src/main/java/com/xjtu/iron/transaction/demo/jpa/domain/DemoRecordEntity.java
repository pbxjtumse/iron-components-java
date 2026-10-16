package com.xjtu.iron.transaction.demo.jpa.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA Demo 实体。
 */
@Entity
@Table(name = "tx_jpa_demo")
public class DemoRecordEntity {

    @Id
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    protected DemoRecordEntity() {
    }

    public DemoRecordEntity(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
}
