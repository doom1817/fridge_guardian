// 全局变量
let turnCount = 0;
const MAX_TURNS = 5;
const token = localStorage.getItem("fg_token");

// 页面加载初始化
document.addEventListener('DOMContentLoaded', () => {
  const input = document.getElementById('taste-input');
  const newChatBtn = document.getElementById('btn-new-chat');

  // 1. 监听输入框：有内容隐藏“新对话”，无内容显示
  input.addEventListener('input', function() {
    if (this.value.trim().length > 0) {
      newChatBtn.classList.add('d-none');
    } else {
      newChatBtn.classList.remove('d-none');
    }
  });

  // 2. 监听回车发送
  input.addEventListener('keydown', function(e) {
    if (e.key === 'Enter') sendFollowUp();
  });
});

// 通用 Fetch 封装
async function fetchWithAuth(url, options = {}) {
  if (!token) {
    window.location.href = "/login";
    return;
  }
  const defaultHeaders = {
    "Authorization": token,
    "Content-Type": "application/json"
  };
  options.headers = { ...defaultHeaders, ...options.headers };

  const res = await fetch(url, options);
  if (res.status === 401) {
    localStorage.removeItem("fg_token");
    window.location.href = "/login";
  }
  return res.json();
}

// === 第一步：生成菜谱 ===
async function generateRecipe() {
  // 收集勾选的食材
  const checkboxes = document.querySelectorAll('input[name="foodIds"]:checked');
  if (checkboxes.length === 0) {
    alert("请至少选择一种食材！");
    return;
  }
  const foodIds = Array.from(checkboxes).map(cb => parseInt(cb.value));

  // UI 状态更新
  setLoading(true, "AI 正在分析食材并设计食谱...");
  document.getElementById('ai-empty-state').classList.add('d-none'); // 隐藏空状态
  document.getElementById('chat-container').innerHTML = ''; // 清空界面

  // 重置逻辑
  turnCount = 0;
  updateStatusText("正在生成...");

  try {
    const res = await fetchWithAuth("/api/ai/generate-recipe", {
      method: "POST",
      body: JSON.stringify(foodIds)
    });

    if (res.code === 200) {
      // 显示菜谱消息
      appendMessage('ai', res.data.content);

      // 成功后：显示底部输入栏、显示轮次徽章
      document.getElementById('input-area').classList.remove('d-none');
      document.getElementById('turn-badge').classList.remove('d-none');
      updateTurnUI();

      // 自动聚焦输入框
      setTimeout(() => document.getElementById('taste-input').focus(), 500);
      updateStatusText("待命：您可以调整口味");
    } else {
      alert(res.message || "生成失败");
      // 失败回退
      document.getElementById('ai-empty-state').classList.remove('d-none');
    }
  } catch (e) {
    console.error(e);
    alert("网络连接错误");
  } finally {
    setLoading(false);
  }
}

// === 第二步：发送口味调整 (对话) ===
async function sendFollowUp() {
  const input = document.getElementById('taste-input');
  const message = input.value.trim();
  if (!message) return;

  if (turnCount >= MAX_TURNS) return;

  // UI 更新
  appendMessage('user', message);
  input.value = '';
  document.getElementById('btn-new-chat').classList.remove('d-none'); // 恢复新对话按钮

  setLoading(true, "AI 正在重新调整方案...");
  updateStatusText("思考中...");

  try {
    const res = await fetchWithAuth("/api/ai/chat", {
      method: "POST",
      body: JSON.stringify({ message: message })
    });

    if (res.code === 200) {
      appendMessage('ai', res.data);
      turnCount++;
      updateTurnUI();
      updateStatusText("待命：您可以继续对话");
    } else {
      appendMessage('ai', "⚠️ 抱歉，我出错了：" + res.message);
    }
  } catch (e) {
    appendMessage('ai', "❌ 网络错误，请重试");
  } finally {
    setLoading(false);
  }
}

// === 第三步：新对话 (清空) ===
async function resetConversation() {
  if (!confirm("确定要放弃当前菜谱并开始新对话吗？")) return;

  try {
    // 调用后端清空记忆
    await fetchWithAuth("/api/ai/clear-history", { method: "POST" });

    // 前端重置
    const container = document.getElementById('chat-container');
    container.innerHTML = '';
    document.getElementById('ai-empty-state').classList.remove('d-none');

    // 隐藏底部栏
    document.getElementById('input-area').classList.add('d-none');
    document.getElementById('turn-badge').classList.add('d-none');

    // 重置变量
    turnCount = 0;
    updateTurnUI();
    updateStatusText("等待指令...");

    // 取消所有勾选
    document.querySelectorAll('input[name="foodIds"]').forEach(cb => cb.checked = false);

  } catch (e) {
    alert("重置失败");
  }
}

// === 辅助函数 ===

// 渲染消息气泡
function appendMessage(role, text) {
  const container = document.getElementById('chat-container');
  const row = document.createElement('div');
  row.className = `message-row ${role}`;

  // 解析 Markdown (如果引入了 marked.js)
  let contentHtml = text;
  if (typeof marked !== 'undefined') {
    contentHtml = marked.parse(text);
  } else {
    // 简单降级处理
    contentHtml = text.replace(/\n/g, '<br>');
  }

  row.innerHTML = `
        <div class="bubble ${role}">
            ${contentHtml}
        </div>
    `;

  container.appendChild(row);
  // 自动滚动到底部
  container.scrollTop = container.scrollHeight;
}

// Loading 遮罩控制
function setLoading(show, text = "") {
  const overlay = document.getElementById('loading-overlay');
  const loadingText = overlay.querySelector('h6');
  if (show) {
    loadingText.textContent = text;
    overlay.classList.remove('d-none');
    overlay.classList.add('d-flex');
  } else {
    overlay.classList.add('d-none');
    overlay.classList.remove('d-flex');
  }
}

// 更新顶部状态文字
function updateStatusText(text) {
  document.querySelector('.status-text').textContent = text;
}

// 更新轮次 UI 和禁用逻辑
function updateTurnUI() {
  document.getElementById('turn-count').innerText = turnCount;
  const input = document.getElementById('taste-input');
  const sendBtn = document.getElementById('btn-send');

  if (turnCount >= MAX_TURNS) {
    input.placeholder = "对话次数已用完，请开启新对话";
    input.disabled = true;
    sendBtn.disabled = true;
    document.getElementById('btn-new-chat').classList.remove('d-none'); // 确保能看到重置按钮
  } else {
    input.placeholder = `口味微调 (剩余${MAX_TURNS - turnCount}次)`;
    input.disabled = false;
    sendBtn.disabled = false;
  }
}