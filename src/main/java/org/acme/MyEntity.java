package org.acme;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;

import java.sql.Blob;

@Entity
public class MyEntity extends PanacheEntity {

    public Blob blob;
}
