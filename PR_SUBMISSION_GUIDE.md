# 提交 PR 步骤指南

## 📋 准备工作清单

- [x] 代码修改完成：在 `ResizableCapacityLinkedBlockingQueue.java` 中为 `capacity` 字段添加 `volatile` 修饰符
- [x] 测试用例编写完成：创建 `CapacityVolatileTest.java` 包含 4 个测试方法
- [x] PR 说明文档准备完成：`PR_DESCRIPTION.md`

## 🚀 提交步骤

### 1. 确认项目仓库信息

Hippo4j 项目的 GitHub 仓库：
- 官方仓库：`https://github.com/opengoofy/hippo4j`
- 你需要先 Fork 这个仓库到你的 GitHub 账号

### 2. Fork 项目（如果还没有 Fork）

1. 访问 https://github.com/opengoofy/hippo4j
2. 点击右上角的 "Fork" 按钮
3. 等待 Fork 完成

### 3. 添加远程仓库（如果还没有配置）

```bash
cd /Users/ruike/Desktop/DevTools/open-source-project/hippo4j-1.5.0

# 查看当前远程仓库
git remote -v

# 如果没有 origin，添加你 Fork 的仓库
git remote add origin https://github.com/YOUR_USERNAME/hippo4j.git

# 添加上游仓库（官方仓库）
git remote add upstream https://github.com/opengoofy/hippo4j.git
```

### 4. 创建新分支

```bash
# 确保在最新的 develop 或 master 分支
git checkout develop  # 或 master，取决于项目的主分支
git pull upstream develop

# 创建新的功能分支
git checkout -b fix/capacity-volatile-modifier
```

### 5. 提交更改

```bash
# 查看修改的文件
git status

# 添加修改的文件
git add hippo4j-common/src/main/java/cn/hippo4j/common/executor/support/ResizableCapacityLinkedBlockingQueue.java
git add hippo4j-common/src/test/java/cn/hippo4j/common/executor/support/CapacityVolatileTest.java

# 提交更改（使用规范的 commit message）
git commit -m "fix: add volatile modifier to capacity field in ResizableCapacityLinkedBlockingQueue

- Add volatile modifier to capacity field to ensure visibility across threads
- Fix potential thread starvation and capacity violation issues
- Add comprehensive test cases to verify the fix
- Follow JDK best practices (same pattern as ThreadPoolExecutor)

Fixes potential issues:
1. Thread starvation when capacity is increased
2. Capacity constraint violations when capacity is decreased
3. Incorrect remainingCapacity() values

This change is 100% backward compatible with minimal performance impact."
```

### 6. 推送到你的 Fork 仓库

```bash
# 推送到你的 GitHub 仓库
git push origin fix/capacity-volatile-modifier
```

### 7. 创建 Pull Request

1. 访问你的 Fork 仓库：`https://github.com/YOUR_USERNAME/hippo4j`
2. 你会看到一个黄色的提示框，显示你刚推送的分支，点击 "Compare & pull request"
3. 填写 PR 信息：

**标题**：
```
fix: add volatile modifier to capacity field in ResizableCapacityLinkedBlockingQueue
```

**描述**：
复制 `PR_DESCRIPTION.md` 的内容，或使用以下精简版：

```markdown
## 🐛 Problem
The `capacity` field in `ResizableCapacityLinkedBlockingQueue` lacks `volatile` modifier, causing visibility issues in multi-threaded environments.

## 🔍 Root Cause
- `setCapacity()` modifies `capacity` without holding any lock
- `put()`/`offer()` read `capacity` under `putLock`
- No happens-before relationship exists → visibility not guaranteed per JMM

## ✅ Solution
Add `volatile` modifier to `capacity` field:
```java
private volatile int capacity;
```

## 📚 Precedent
Follows the same pattern as JDK's `ThreadPoolExecutor.maximumPoolSize`

## 🧪 Testing
Added `CapacityVolatileTest` with 4 comprehensive test cases

## 📊 Impact
- ✅ Fixes potential deadlock and capacity violation issues
- ✅ 100% backward compatible
- ✅ Minimal performance impact
- ✅ Follows JDK best practices

## 📖 References
- [JLS Chapter 17: Threads and Locks](https://docs.oracle.com/javase/specs/jls/se8/html/jls-17.html)
- [ThreadPoolExecutor Source](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/concurrent/ThreadPoolExecutor.java)
```

4. 选择目标分支（通常是 `develop` 或 `master`）
5. 点击 "Create pull request"

### 8. 等待 Review

- 项目维护者会 review 你的 PR
- 可能会有一些讨论或修改建议
- 根据反馈进行必要的修改
- 修改后推送到同一分支，PR 会自动更新

## 📝 Commit Message 规范

Hippo4j 项目可能遵循 Conventional Commits 规范：

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Type**:
- `fix`: Bug 修复
- `feat`: 新功能
- `docs`: 文档更新
- `test`: 测试相关
- `refactor`: 重构
- `perf`: 性能优化
- `chore`: 构建/工具相关

**示例**:
```
fix(queue): add volatile modifier to capacity field

- Ensure visibility of capacity changes across threads
- Fix potential thread starvation and capacity violations
- Add comprehensive test coverage

Fixes #<issue-number> (if applicable)
```

## 🎯 注意事项

1. **代码风格**：确保代码符合项目的代码规范
2. **测试**：确保所有测试通过（运行 `mvn test`）
3. **文档**：如果需要，更新相关文档
4. **签署 CLA**：某些项目需要签署贡献者许可协议
5. **保持更新**：如果 PR review 时间较长，记得定期 rebase 最新的上游代码

## 🔧 常用 Git 命令

```bash
# 同步上游最新代码
git fetch upstream
git rebase upstream/develop

# 修改最后一次 commit
git commit --amend

# 强制推送（rebase 后需要）
git push origin fix/capacity-volatile-modifier --force

# 查看提交历史
git log --oneline

# 撤销本地修改
git checkout -- <file>
```

## 📞 需要帮助？

如果在提交 PR 过程中遇到问题：
1. 查看项目的 CONTRIBUTING.md 文件
2. 在项目的 Issue 或 Discussion 中提问
3. 参考其他已合并的 PR

祝你的 PR 顺利合并！🎉
