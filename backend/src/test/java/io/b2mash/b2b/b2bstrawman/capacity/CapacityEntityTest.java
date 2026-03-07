package io.b2mash.b2b.b2bstrawman.capacity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CapacityEntityTest {

  private static final String ORG_ID = "org_capacity_entity_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private MemberRepository memberRepository;
  @Autowired private MemberCapacityRepository capacityRepository;
  @Autowired private ResourceAllocationRepository allocationRepository;
  @Autowired private LeaveBlockRepository leaveBlockRepository;
  @Autowired private ProjectService projectService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;

  private String schemaName;
  private UUID memberId;
  private UUID projectId;

  @BeforeAll
  void provisionTenant() {
    schemaName =
        provisioningService.provisionTenant(ORG_ID, "Capacity Entity Test Org").schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var member =
                  new Member("clerk_cap_test", "cap@test.com", "Cap Tester", null, "owner");
              member = memberRepository.save(member);
              memberId = member.getId();

              ScopedValue.where(RequestScopes.MEMBER_ID, memberId)
                  .run(
                      () -> {
                        Project project =
                            projectService.createProject("Cap Test Project", "Test", memberId);
                        projectId = project.getId();
                      });
            });
  }

  @Test
  void memberCapacityRoundTrip() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              // Monday
              var cap =
                  new MemberCapacity(
                      memberId,
                      new BigDecimal("40.00"),
                      LocalDate.of(2025, 3, 3),
                      null,
                      "Standard hours",
                      memberId);
              var saved = capacityRepository.save(cap);
              capacityRepository.flush();

              var found = capacityRepository.findById(saved.getId()).orElseThrow();
              assertThat(found.getMemberId()).isEqualTo(memberId);
              assertThat(found.getWeeklyHours()).isEqualByComparingTo(new BigDecimal("40.00"));
              assertThat(found.getEffectiveFrom()).isEqualTo(LocalDate.of(2025, 3, 3));
              assertThat(found.getEffectiveTo()).isNull();
              assertThat(found.getNote()).isEqualTo("Standard hours");
              assertThat(found.getCreatedBy()).isEqualTo(memberId);
              assertThat(found.getCreatedAt()).isNotNull();
              assertThat(found.getUpdatedAt()).isNotNull();
            });
  }

  @Test
  void effectiveCapacityResolution() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              // Use a dedicated member to avoid interference from other tests
              var member =
                  new Member("clerk_eff_cap", "effcap@test.com", "Eff Cap Tester", null, "member");
              member = memberRepository.save(member);
              var effMemberId = member.getId();

              // Create two capacity records: one past (closed), one current (open-ended)
              var pastCap =
                  new MemberCapacity(
                      effMemberId,
                      new BigDecimal("20.00"),
                      LocalDate.of(2025, 1, 6), // Monday
                      LocalDate.of(2025, 2, 28),
                      "Part-time",
                      effMemberId);
              capacityRepository.save(pastCap);

              var currentCap =
                  new MemberCapacity(
                      effMemberId,
                      new BigDecimal("35.00"),
                      LocalDate.of(2025, 3, 10), // Monday
                      null,
                      "Reduced hours",
                      effMemberId);
              capacityRepository.save(currentCap);
              capacityRepository.flush();

              // Query for a date in the past range
              var pastResults =
                  capacityRepository.findEffectiveCapacity(effMemberId, LocalDate.of(2025, 2, 15));
              assertThat(pastResults).isNotEmpty();
              assertThat(pastResults.getFirst().getWeeklyHours())
                  .isEqualByComparingTo(new BigDecimal("20.00"));

              // Query for a date in the current range
              var currentResults =
                  capacityRepository.findEffectiveCapacity(effMemberId, LocalDate.of(2025, 4, 1));
              assertThat(currentResults).isNotEmpty();
              assertThat(currentResults.getFirst().getWeeklyHours())
                  .isEqualByComparingTo(new BigDecimal("35.00"));

              // Query for a date with no coverage (gap between past and current)
              var gapResults =
                  capacityRepository.findEffectiveCapacity(effMemberId, LocalDate.of(2025, 3, 5));
              assertThat(gapResults).isEmpty();
            });
  }

  @Test
  void resourceAllocationRoundTrip() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var allocation =
                  new ResourceAllocation(
                      memberId,
                      projectId,
                      LocalDate.of(2025, 3, 3), // Monday
                      new BigDecimal("8.00"),
                      "Sprint work",
                      memberId);
              var saved = allocationRepository.save(allocation);
              allocationRepository.flush();

              var found = allocationRepository.findById(saved.getId()).orElseThrow();
              assertThat(found.getMemberId()).isEqualTo(memberId);
              assertThat(found.getProjectId()).isEqualTo(projectId);
              assertThat(found.getWeekStart()).isEqualTo(LocalDate.of(2025, 3, 3));
              assertThat(found.getAllocatedHours()).isEqualByComparingTo(new BigDecimal("8.00"));
              assertThat(found.getNote()).isEqualTo("Sprint work");
              assertThat(found.getCreatedBy()).isEqualTo(memberId);
            });
  }

  @Test
  void uniqueConstraintAllocationMemberProjectWeek() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var monday = LocalDate.of(2025, 3, 17); // Monday
              var a1 =
                  new ResourceAllocation(
                      memberId, projectId, monday, new BigDecimal("4.00"), null, memberId);
              allocationRepository.save(a1);
              allocationRepository.flush();

              var a2 =
                  new ResourceAllocation(
                      memberId, projectId, monday, new BigDecimal("6.00"), null, memberId);
              assertThatThrownBy(
                      () -> {
                        allocationRepository.save(a2);
                        allocationRepository.flush();
                      })
                  .isInstanceOf(DataIntegrityViolationException.class);
            });
  }

  @Test
  void sumAllocatedHoursForMemberWeek() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              // Create a second project for a different allocation
              final UUID[] project2Id = new UUID[1];
              ScopedValue.where(RequestScopes.MEMBER_ID, memberId)
                  .run(
                      () -> {
                        Project p2 =
                            projectService.createProject("Cap Test Project 2", "Test2", memberId);
                        project2Id[0] = p2.getId();
                      });

              var monday = LocalDate.of(2025, 3, 24); // Monday
              allocationRepository.save(
                  new ResourceAllocation(
                      memberId, projectId, monday, new BigDecimal("10.00"), null, memberId));
              allocationRepository.save(
                  new ResourceAllocation(
                      memberId, project2Id[0], monday, new BigDecimal("15.00"), null, memberId));
              allocationRepository.flush();

              var sum = allocationRepository.sumAllocatedHoursForMemberWeek(memberId, monday);
              assertThat(sum).isEqualByComparingTo(new BigDecimal("25.00"));

              // No allocations for a different week
              var emptySum =
                  allocationRepository.sumAllocatedHoursForMemberWeek(
                      memberId, LocalDate.of(2025, 4, 7));
              assertThat(emptySum).isEqualByComparingTo(BigDecimal.ZERO);
            });
  }

  @Test
  void leaveBlockOverlappingQuery() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var leave =
                  new LeaveBlock(
                      memberId,
                      LocalDate.of(2025, 3, 10),
                      LocalDate.of(2025, 3, 14),
                      "Vacation",
                      memberId);
              leaveBlockRepository.save(leave);
              leaveBlockRepository.flush();

              // Overlapping range
              var overlapping =
                  leaveBlockRepository.findByMemberIdAndOverlapping(
                      memberId, LocalDate.of(2025, 3, 12), LocalDate.of(2025, 3, 20));
              assertThat(overlapping).hasSize(1);
              assertThat(overlapping.getFirst().getNote()).isEqualTo("Vacation");

              // Non-overlapping range
              var noOverlap =
                  leaveBlockRepository.findByMemberIdAndOverlapping(
                      memberId, LocalDate.of(2025, 3, 15), LocalDate.of(2025, 3, 20));
              assertThat(noOverlap).isEmpty();
            });
  }

  @Test
  void defaultWeeklyCapacityHoursRoundTrip() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var settings = orgSettingsRepository.findAll().getFirst();
              settings.setDefaultWeeklyCapacityHours(new BigDecimal("37.50"));
              orgSettingsRepository.save(settings);
              orgSettingsRepository.flush();

              var found = orgSettingsRepository.findById(settings.getId()).orElseThrow();
              assertThat(found.getDefaultWeeklyCapacityHours())
                  .isEqualByComparingTo(new BigDecimal("37.50"));
            });
  }

  @Test
  void weekStartMustBeMondayForAllocation() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              // Tuesday = not valid
              var allocation =
                  new ResourceAllocation(
                      memberId,
                      projectId,
                      LocalDate.of(2025, 3, 4), // Tuesday
                      new BigDecimal("8.00"),
                      null,
                      memberId);
              assertThatThrownBy(
                      () -> {
                        allocationRepository.save(allocation);
                        allocationRepository.flush();
                      })
                  .isInstanceOf(DataIntegrityViolationException.class);
            });
  }

  @Test
  void effectiveFromMustBeMondayForCapacity() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              // Wednesday = not valid
              var cap =
                  new MemberCapacity(
                      memberId,
                      new BigDecimal("40.00"),
                      LocalDate.of(2025, 3, 5), // Wednesday
                      null,
                      null,
                      memberId);
              assertThatThrownBy(
                      () -> {
                        capacityRepository.save(cap);
                        capacityRepository.flush();
                      })
                  .isInstanceOf(DataIntegrityViolationException.class);
            });
  }

  @Test
  void allocatedHoursMustBePositiveAndBounded() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              // Zero hours = invalid
              var zeroAlloc =
                  new ResourceAllocation(
                      memberId,
                      projectId,
                      LocalDate.of(2025, 3, 31), // Monday
                      BigDecimal.ZERO,
                      null,
                      memberId);
              assertThatThrownBy(
                      () -> {
                        allocationRepository.save(zeroAlloc);
                        allocationRepository.flush();
                      })
                  .isInstanceOf(DataIntegrityViolationException.class);
            });
  }

  @Test
  void endDateMustNotBeBeforeStartDate() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var invalidLeave =
                  new LeaveBlock(
                      memberId,
                      LocalDate.of(2025, 3, 15),
                      LocalDate.of(2025, 3, 10), // end before start
                      null,
                      memberId);
              assertThatThrownBy(
                      () -> {
                        leaveBlockRepository.save(invalidLeave);
                        leaveBlockRepository.flush();
                      })
                  .isInstanceOf(DataIntegrityViolationException.class);
            });
  }

  @Test
  void weeklyHoursMustBePositive() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var invalidCap =
                  new MemberCapacity(
                      memberId,
                      BigDecimal.ZERO,
                      LocalDate.of(2025, 4, 7), // Monday
                      null,
                      null,
                      memberId);
              assertThatThrownBy(
                      () -> {
                        capacityRepository.save(invalidCap);
                        capacityRepository.flush();
                      })
                  .isInstanceOf(DataIntegrityViolationException.class);
            });
  }

  @Test
  void cascadeDeleteOnMemberDeletion() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              // Create a separate member for this test
              var member =
                  new Member("clerk_cascade", "cascade@test.com", "Cascade", null, "member");
              member = memberRepository.save(member);
              var mId = member.getId();

              var cap =
                  new MemberCapacity(
                      mId,
                      new BigDecimal("40.00"),
                      LocalDate.of(2025, 3, 3), // Monday
                      null,
                      null,
                      mId);
              capacityRepository.save(cap);

              var leave =
                  new LeaveBlock(
                      mId, LocalDate.of(2025, 3, 10), LocalDate.of(2025, 3, 14), null, mId);
              leaveBlockRepository.save(leave);
              leaveBlockRepository.flush();

              assertThat(capacityRepository.findByMemberIdOrderByEffectiveFromDesc(mId)).hasSize(1);
              assertThat(leaveBlockRepository.findByMemberIdOrderByStartDateDesc(mId)).hasSize(1);

              memberRepository.delete(member);
              memberRepository.flush();

              assertThat(capacityRepository.findByMemberIdOrderByEffectiveFromDesc(mId)).isEmpty();
              assertThat(leaveBlockRepository.findByMemberIdOrderByStartDateDesc(mId)).isEmpty();
            });
  }

  @Test
  void findByMemberIdAndWeekStartBetween() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var mon1 = LocalDate.of(2025, 4, 14); // Monday
              var mon2 = LocalDate.of(2025, 4, 21); // Monday
              var mon3 = LocalDate.of(2025, 4, 28); // Monday

              allocationRepository.save(
                  new ResourceAllocation(
                      memberId, projectId, mon1, new BigDecimal("5.00"), null, memberId));
              allocationRepository.save(
                  new ResourceAllocation(
                      memberId, projectId, mon2, new BigDecimal("6.00"), null, memberId));
              allocationRepository.save(
                  new ResourceAllocation(
                      memberId, projectId, mon3, new BigDecimal("7.00"), null, memberId));
              allocationRepository.flush();

              var results =
                  allocationRepository.findByMemberIdAndWeekStartBetween(memberId, mon1, mon2);
              assertThat(results).hasSize(2);
            });
  }

  @Test
  void findByProjectIdAndWeekStartBetween() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              // Use the allocations created in previous test (mon1, mon2, mon3 for projectId)
              var results =
                  allocationRepository.findByProjectIdAndWeekStartBetween(
                      projectId, LocalDate.of(2025, 4, 14), LocalDate.of(2025, 4, 28));
              assertThat(results).hasSizeGreaterThanOrEqualTo(3);
            });
  }

  @Test
  void findByWeekStartBetween() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var results =
                  allocationRepository.findByWeekStartBetween(
                      LocalDate.of(2025, 4, 14), LocalDate.of(2025, 4, 28));
              assertThat(results).hasSizeGreaterThanOrEqualTo(3);
            });
  }

  @Test
  void findByMemberIdAndProjectIdAndWeekStart() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var monday = LocalDate.of(2025, 4, 14);
              var found =
                  allocationRepository.findByMemberIdAndProjectIdAndWeekStart(
                      memberId, projectId, monday);
              assertThat(found).isPresent();
              assertThat(found.get().getAllocatedHours())
                  .isEqualByComparingTo(new BigDecimal("5.00"));

              // Non-existent combo
              var notFound =
                  allocationRepository.findByMemberIdAndProjectIdAndWeekStart(
                      memberId, projectId, LocalDate.of(2025, 5, 5));
              assertThat(notFound).isEmpty();
            });
  }

  @Test
  void findAllOverlappingLeave() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              // Create a leave block specifically for this test
              var leave =
                  new LeaveBlock(
                      memberId,
                      LocalDate.of(2025, 6, 2),
                      LocalDate.of(2025, 6, 6),
                      "Summer leave",
                      memberId);
              leaveBlockRepository.save(leave);
              leaveBlockRepository.flush();

              var allOverlapping =
                  leaveBlockRepository.findAllOverlapping(
                      LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30));
              assertThat(allOverlapping).isNotEmpty();
              assertThat(allOverlapping.getFirst().getNote()).isEqualTo("Summer leave");
            });
  }
}
