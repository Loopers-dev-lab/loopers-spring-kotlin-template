# Loopers Template (Spring + Kotlin)
Loopers 에서 제공하는 스프링 코틀린 템플릿 프로젝트입니다.

## Getting Started
현재 프로젝트 안정성 및 유지보수성 등을 위해 아래와 같은 장치를 운용하고 있습니다. 이에 아래 명령어를 통해 프로젝트의 기반을 설치해주세요.
### Setup
- pre-commit : 커밋 이전에 `ktlint` 를 통해 점검, 코드 안정성 확보
```shell
make init
```
### Environment
`local` 프로필로 동작할 수 있도록, 필요 인프라를 `docker-compose` 로 제공합니다.
```shell
docker-compose -f ./docker/infra-compose.yml up
```
### Monitoring
`local` 환경에서 모니터링을 할 수 있도록, `docker-compose` 를 통해 `prometheus` 와 `grafana` 를 제공합니다.

애플리케이션 실행 이후, **http://localhost:3000** 로 접속해, admin/admin 계정으로 로그인하여 확인하실 수 있습니다.
```shell
docker-compose -f ./docker/monitoring-compose.yml up
```

## Port Configuration
각 애플리케이션은 포트 충돌을 방지하기 위해 다음과 같이 포트를 할당합니다.

### Application Ports
| Application | Port | Description |
|------------|------|-------------|
| commerce-api | 8080 | REST API Server |
| pg-simulator | 8082 | Payment Gateway Simulator |
| commerce-streamer | 8083 | Kafka Event Streaming |
| commerce-batch | 8085 | Spring Batch Jobs |

### Management Ports (Actuator)
| Application | Port | Description |
|------------|------|-------------|
| All Applications | 8081 | Actuator / Prometheus Metrics |
| pg-simulator | 8083 | PG Actuator |
| commerce-streamer | 8084 | Streamer Actuator |

### Infrastructure (Docker)
| Service | Port | Description |
|---------|------|-------------|
| MySQL | 3306 | Database |
| Redis | 6379 | Cache / Ranking Store |
| Kafka | 9092 | Message Broker |
| Zookeeper | 2181 | Kafka Coordinator |
| Grafana | 3000 | Monitoring Dashboard |
| Prometheus | 9090 | Metrics Collector |

**자세한 내용**: [포트 설정 가이드](.codeguide/round10/PORT_CONFIGURATION.md)

---

## About Multi-Module Project
본 프로젝트는 멀티 모듈 프로젝트로 구성되어 있습니다. 각 모듈의 위계 및 역할을 분명히 하고, 아래와 같은 규칙을 적용합니다.

- apps : 각 모듈은 실행가능한 **SpringBootApplication** 을 의미합니다.
- modules : 특정 구현이나 도메인에 의존적이지 않고, reusable 한 configuration 을 원칙으로 합니다.
- supports : logging, monitoring 과 같이 부가적인 기능을 지원하는 add-on 모듈입니다.

```
Root
├── apps ( spring-applications )
│   ├── 📦 commerce-api
│   ├── 📦 commerce-batch
│   ├── 📦 commerce-streamer
│   └── 📦 pg-simulator
├── modules ( reusable-configurations )
│   ├── 📦 jpa
│   ├── 📦 redis
│   └── 📦 kafka
└── supports ( add-ons )
    ├── 📦 monitoring
    ├── 📦 logging
    └── 📦 jackson
```
