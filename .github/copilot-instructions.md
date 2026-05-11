# Copilot Instructions for bata Project

## 项目架构概览
- 本项目为 Java Maven 项目，核心代码位于 `src/main/java/bata/`，包含主要业务逻辑、数据访问层（dao）、模型（model）、协议（protocol）等模块。
- 资源文件存放于 `src/main/resources/`，测试代码位于 `src/test/java/bata/`。
- Docker 支持文件在 `docker/` 目录下，包含 `compose.yml`、初始化 SQL（`init.sql`）等，便于本地或生产环境部署。

## 关键开发流程
- **构建项目**：使用 Maven，常用命令：
  ```powershell
  mvn clean package
  ```
  生成的 jar 包位于 `target/bata-plugin-1.0-SNAPSHOT.jar`。
- **运行测试**：
  ```powershell
  mvn test
  ```
  测试报告输出在 `target/surefire-reports/`。
- **调试与开发**：
  - 推荐使用 IDE（如 IntelliJ IDEA 或 VS Code）进行断点调试。
  - 主要入口或核心逻辑在 `Plugin.java`。

## 项目约定与模式
- **包结构清晰**：
  - `dao/` 负责数据访问，通常与数据库表结构对应。
  - `model/` 定义数据模型。
  - `protocol/` 处理协议相关逻辑。
- **数据库初始化**：
  - 初始化 SQL 文件为 `docker/init/init.sql`，用于容器化部署时自动建表。
- **依赖管理**：
  - 所有依赖声明在 `pom.xml`，如需添加第三方库请修改此文件并执行 Maven 构建。
- **测试规范**：
  - 测试类与被测类同名，后缀为 `Test`，如 `RoomTest.java` 测试 `Room.java`。

## 集成与外部依赖
- **Docker 部署**：
  - 使用 `docker/compose.yml` 进行服务编排，支持数据库初始化和数据挂载。
- **数据库**：
  - 项目假定有数据库支持，相关配置和初始化见 `docker/init/init.sql`。

## 代码风格与协作建议
- 遵循标准 Java 命名与分层模式。
- 变更核心逻辑时，务必同步更新相关测试。
- 参考 `src/main/java/bata/` 下各包的结构和命名，保持一致性。

## 示例：新增数据模型
1. 在 `model/` 下创建新类，如 `User.java`。
2. 在 `dao/` 下添加对应数据访问类，如 `UserDao.java`。
3. 在 `test/model/` 下添加测试类 `UserTest.java`。
4. 如涉及数据库表变更，同步更新 `init.sql`。

---
如有不清楚或遗漏的部分，请反馈以便进一步完善说明。