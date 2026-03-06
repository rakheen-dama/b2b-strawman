package io.b2mash.b2b.b2bstrawman.informationrequest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestNumberService {

  @PersistenceContext private EntityManager entityManager;

  @Transactional
  public String allocateNumber() {
    var result =
        entityManager
            .createNativeQuery(
                "INSERT INTO request_counters (id, next_number, singleton)"
                    + " VALUES (gen_random_uuid(), 2, TRUE)"
                    + " ON CONFLICT ON CONSTRAINT request_counters_singleton"
                    + " DO UPDATE SET next_number = request_counters.next_number + 1"
                    + " RETURNING next_number - 1")
            .getSingleResult();
    int number = ((Number) result).intValue();
    return String.format("REQ-%04d", number);
  }
}
