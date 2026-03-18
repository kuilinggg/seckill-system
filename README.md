## 1. 系统架构草图（演进式设计）

~~~mermaid
graph TD
    C[客户端<br/>Web/App/小程序] --> G[API网关 Gateway<br/>鉴权 路由 限流 灰度]

    subgraph S[微服务层]
        U[user-service<br/>用户与认证]
        P[product-service<br/>商品与活动]
        O[order-service<br/>订单与交易]
        I[inventory-service<br/>库存与扣减]
    end

    G --> U
    G --> P
    G --> O
    G --> I

    subgraph D[数据与中间件层]
        MYSQL[(MySQL<br/>事务数据)]
        REDIS[(Redis<br/>缓存 分布式锁 热点防护)]
        MQ[(RocketMQ / RabbitMQ<br/>异步削峰 最终一致性)]
    end

    U --> MYSQL
    P --> MYSQL
    O --> MYSQL
    I --> MYSQL

    U --> REDIS
    P --> REDIS
    O --> REDIS
    I --> REDIS

    O --> MQ
    I --> MQ
    P --> MQ

    MQ --> O
    MQ --> I

    %% 演进愿景能力
    G -.-> SL[Sentinel<br/>流控 熔断 降级]
    U -.-> NC[Nacos<br/>注册发现 配置中心]
    P -.-> NC
    O -.-> NC
    I -.-> NC
~~~

设计说明（演进思路）：
1. 第一阶段先保证主链路可跑通：用户登录、商品查询、下单扣库存。
2. 第二阶段引入 Redis 预扣与多级缓存，降低 DB 压力。
3. 第三阶段引入 MQ 做异步削峰与订单状态流转，提升峰值吞吐与系统稳定性。
4. 第四阶段完善 Sentinel + Nacos，实现流控、熔断、动态配置、弹性扩缩容。

---

## 2. 核心业务 API 接口定义（RESTful）

| 接口名称 | HTTP 方法 | 路径 | 功能简述 |
|---|---|---|---|
| 用户注册 | POST | /api/v1/users | 创建新用户账号，完成注册 |
| 用户登录 | POST | /api/v1/sessions | 创建登录会话，返回访问令牌 |
| 获取秒杀商品列表 | GET | /api/v1/seckill-products | 分页查询当前可参与秒杀的商品列表 |
| 获取商品详情 | GET | /api/v1/seckill-products/{productId} | 查询指定秒杀商品详情（含库存与活动信息） |
| 执行秒杀下单 | POST | /api/v1/seckill-orders | 提交秒杀请求，进行资格校验、库存预占与订单创建 |

接口设计要点：
1. 登录使用“创建会话”语义，符合 REST 资源建模思想。
2. 秒杀下单建议要求幂等键（如 Idempotency-Key），避免重复提交。
3. 对秒杀下单接口应在网关和服务层双重限流，保护核心依赖。

---

## 3. 数据库 ER 图设计（含冻结库存）

~~~mermaid
erDiagram
    USER {
        bigint id PK
        varchar username
        varchar password_hash
        varchar phone
        tinyint status
        datetime created_at
        datetime updated_at
    }

    PRODUCT {
        bigint id PK
        varchar product_name
        decimal seckill_price
        decimal original_price
        datetime seckill_start_time
        datetime seckill_end_time
        tinyint status
        datetime created_at
        datetime updated_at
    }

    INVENTORY {
        bigint id PK
        bigint product_id FK
        int total_stock
        int available_stock
        int frozen_stock
        int sold_stock
        int version
        datetime created_at
        datetime updated_at
    }

    ORDER {
        bigint id PK
        bigint user_id FK
        bigint product_id FK
        varchar order_no
        int buy_count
        decimal order_amount
        varchar order_status
        datetime created_at
        datetime updated_at
    }

    USER ||--o{ ORDER : places
    PRODUCT ||--o{ ORDER : contains
    PRODUCT ||--|| INVENTORY : owns
~~~

建模说明：
1. INVENTORY 增加 frozen_stock，用于“预占库存”防止超卖。
2. INVENTORY.version 用于乐观锁更新，配合条件更新实现并发安全扣减。
3. ORDER 与 USER、PRODUCT 采用多对一关系，支持用户多次下单与商品多订单聚合分析。

---

## 4. 技术栈选型与理由（高并发秒杀场景）

Java 17 + Spring Boot 3.x 提供现代化、长期支持的服务端基础，具备更好的性能与生态兼容性；Spring Cloud Alibaba（Nacos、Sentinel）可快速构建微服务治理能力，包括注册发现、配置动态化、流控熔断与降级，是秒杀场景下保障可用性的关键；MyBatis-Plus 在保证 SQL 可控性的同时提升开发效率，适合订单与库存等强业务语义模块；MySQL 承担核心事务数据的一致性存储；Redis 负责热点数据缓存、分布式锁与高频读写承压；MQ（RocketMQ/RabbitMQ）用于异步削峰、解耦与最终一致性处理。整套方案兼顾了“高并发抗压能力、事务一致性、工程落地效率与面试可讲深度”，非常适合作为秋招阶段的企业级项目主线。  
