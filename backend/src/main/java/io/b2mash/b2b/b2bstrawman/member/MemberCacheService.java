package io.b2mash.b2b.b2bstrawman.member;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MemberCacheService {

  public record MemberInfo(UUID memberId, String orgRole) {}

  private final Cache<String, MemberInfo> cache =
      Caffeine.newBuilder().maximumSize(50_000).expireAfterWrite(Duration.ofHours(1)).build();

  private static String key(String tenantId, String userId) {
    return tenantId + ":" + userId;
  }

  public MemberInfo get(String tenantId, String userId) {
    return cache.getIfPresent(key(tenantId, userId));
  }

  public void put(String tenantId, String userId, MemberInfo info) {
    cache.put(key(tenantId, userId), info);
  }

  public void evict(String tenantId, String userId) {
    cache.invalidate(key(tenantId, userId));
  }

  public void evictAll() {
    cache.invalidateAll();
  }
}
