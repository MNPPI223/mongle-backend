package com.mongle.backend.reflection;

import com.mongle.backend.reflection.dto.RiskResource;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * API_명세서_v1 4.3.1 — 1차 키워드 리스트(초안, 팀 검수 필요 🔴). 형태소 분석 없이 단순 포함
 * 매칭으로 시작한다. application.yml의 mongle.risk.keywords에서 팀이 바로 조정할 수 있다.
 */
@Component
public class RiskKeywordDetector {

    private static final List<RiskResource> RESOURCES = List.of(
            new RiskResource("자살예방상담전화(안심콜)", "0199"),
            new RiskResource("긴급신고", "119")
    );

    private final List<String> keywords;

    public RiskKeywordDetector(
            @Value("#{'${mongle.risk.keywords}'.split(',')}") List<String> keywords) {
        // matches()가 입력 텍스트의 공백을 전부 제거하고 비교하므로, "극단적 선택"처럼 공백이
        // 든 키워드를 yml에 그대로 적으면 조용히 매칭이 안 된다(2026-11-09 실제 curl 검증 중
        // 발견). 키워드 쪽도 똑같이 공백을 제거해서 이 클래스 안에서 규칙이 항상 일관되게 한다.
        this.keywords = keywords.stream()
                .map(k -> k.trim().replaceAll("\\s+", ""))
                .filter(k -> !k.isBlank())
                .toList();
    }

    public boolean matches(String text) {
        String normalized = text.replaceAll("\\s+", "");
        return keywords.stream().anyMatch(normalized::contains);
    }

    public List<RiskResource> resources() {
        return RESOURCES;
    }
}
