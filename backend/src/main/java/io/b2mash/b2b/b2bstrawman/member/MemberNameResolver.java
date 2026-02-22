package io.b2mash.b2b.b2bstrawman.member;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MemberNameResolver {

  private final MemberRepository memberRepository;

  public MemberNameResolver(MemberRepository memberRepository) {
    this.memberRepository = memberRepository;
  }

  public String resolveName(UUID memberId) {
    return memberRepository.findById(memberId).map(Member::getName).orElse("Unknown");
  }

  public String resolveNameOrNull(UUID memberId) {
    return memberRepository.findById(memberId).map(Member::getName).orElse(null);
  }

  public Map<UUID, String> resolveNames(Collection<UUID> memberIds) {
    if (memberIds.isEmpty()) return Map.of();

    return memberRepository.findAllById(memberIds).stream()
        .collect(
            Collectors.toMap(
                Member::getId, m -> m.getName() != null ? m.getName() : "", (a, b) -> a));
  }
}
