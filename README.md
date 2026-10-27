# FOJ 代码沙箱

Java 原生实现的安全代码沙箱服务，供 [foj-backend](https://github.com/Hob/foj-backend)
判题时调用：接收代码与输入用例，在 Docker 容器中隔离编译执行，返回运行结果。

## 安全设计

- **容器隔离**：用户代码在一次性 Docker 容器内执行，与宿主机完全隔离；
- **资源限制**：限制容器内存、运行超时时间，防止恶意代码拖垮判题机；
- **用例执行**：逐用例运行，收集 耗时 / 内存 / 标准输出。

## 核心流程

接收 ExecuteCodeRequest(代码 + 输入用例列表)
→ 编译（javac，Docker 内）
→ 逐用例执行（java，Docker 内，限时）
→ 收集每次运行的 耗时/内存/输出
→ 返回 ExecuteCodeResponse


## 接口

POST `http://<host>:8090/executeCode`

请求/响应字段见 `model` 包下 `ExecuteCodeRequest / ExecuteCodeResponse`。

## 快速启动

1. 本机安装 Docker Desktop，确保 `docker -v` 可用；
2. 拉取执行环境镜像（首次自动拉取或手动 `docker pull openjdk:17`，按实际为准）；
3. 运行 `MainApplication`，服务监听 8090。
