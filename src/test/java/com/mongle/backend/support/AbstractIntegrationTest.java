package com.mongle.backend.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 모든 통합테스트가 상속받는 베이스. H2가 아니라 Testcontainers로 띄운 진짜 MySQL 8.4 위에서
 * 돈다 — 운영 스키마(CHAR/VARCHAR, ENUM 흉내 등)와 다르게 동작해서 한 번 사고 난 적이 있어서
 * (README "명세서 대비 의도적으로 보강/변경한 지점" 참고) 아예 같은 DB로 통일했다.
 *
 * 컨테이너는 static이라 이 클래스를 상속하는 모든 테스트 클래스가 JVM 안에서 하나를 공유한다
 * — Flyway 마이그레이션도 전체 테스트 스위트에서 딱 한 번만 돈다. 로컬·CI(GitHub Actions
 * ubuntu-latest) 둘 다 Docker가 있어야 돈다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("mongle")
            .withUsername("mongle")
            .withPassword("mongle-test");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}
