// 全局变量
let turnCount = 0;
const MAX_TURNS = 5;
const token = localStorage.getItem("fg_token");

// 页面加载初始化
document.addEventListener('DOMContentLoaded', () => {
  // 1. 加载食材列表 (修复 Bug 的核心)
  loadFoodList();

  // 2. 绑定输入框交互
  const input = document.getElementById('taste-input');
  const newChatBtn = document.getElementById('btn-new-chat');

  input.addEventListener('input', function() {
    if (this.value.trim().length > 0) {
      newChatBtn.classList.add('d-none');
    } else {
      newChatBtn.classList.remove('d-none');
    }
  });

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

// === 新增：加载食材列表 ===
async function loadFoodList() {
  const container = document.getElementById('food-list-container');

  try {
    const res = await fetchWithAuth("/api/food/list"); // 调用已有的列表接口
    if (res.code === 200) {
      const foods = res.data;
      container.innerHTML = ''; // 清空 loading

      if (foods.length === 0) {
        // 显示空状态
        const tpl = document.getElementById('tpl-empty-state');
        container.appendChild(tpl.content.cloneNode(true));
      } else {
        // 渲染列表
        foods.forEach(food => {
          const div = document.createElement('div');
          div.className = 'form-check custom-check mb-2';
          div.innerHTML = `
                        <input class="form-check-input" type="checkbox" name="foodIds" value="${food.id}" id="food-${food.id}">
                        <label class="form-check-label w-100" for="food-${food.id}">
                            <span class="fw-medium">${food.name}</span>
                            <small class="text-muted float-end">${food.quantity}${food.unit}</small>
                        </label>
                    `;
          container.appendChild(div);
        });
      }
    } else {
      container.innerHTML = '<div class="text-center text-danger py-5">加载失败: ' + res.message + '</div>';
    }
  } catch (e) {
    console.error(e);
    container.innerHTML = '<div class="text-center text-danger py-5">网络连接错误</div>';
  }
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
// 安全地操作 DOM
  const emptyState = document.getElementById('ai-empty-state');
  if(emptyState) emptyState.classList.add('d-none'); // 隐藏空状态

  document.getElementById('chat-container').innerHTML = ''; // 清空聊天记录
  turnCount = 0;
  updateStatusText("正在生成...");

  try {
    const res = await fetchWithAuth("/api/ai/generate-recipe", {
      method: "POST",
      body: JSON.stringify(foodIds)
    });

    if (res.code === 200) {
      appendMessage('ai', res.data.content);
      document.getElementById('input-area').classList.remove('d-none');
      document.getElementById('turn-badge').classList.remove('d-none');
      updateTurnUI();
      setTimeout(() => document.getElementById('taste-input').focus(), 500);
      updateStatusText("待命：您可以调整口味");
    } else {
      alert(res.message || "生成失败");
      // 失败回退：显示空状态
      if(emptyState) emptyState.classList.remove('d-none');
    }
  } catch (e) {
    console.error(e);
    alert("网络连接错误");
    // 失败回退
    if(emptyState) emptyState.classList.remove('d-none');
  } finally {
    setLoading(false); // 无论成功失败，都停止转圈
  }
}

// === 第二步：发送口味调整 (对话) ===
async function sendFollowUp() {
  const input = document.getElementById('taste-input');
  const message = input.value.trim();
  if (!message) return;

  if (turnCount >= MAX_TURNS) return;

  appendMessage('user', message);
  input.value = '';
  document.getElementById('btn-new-chat').classList.remove('d-none');

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

// === 第三步：新对话 (触发弹窗) ===
function resetConversation() {
  // 不再直接 confirm，而是显示自定义 Modal
  const modal = document.getElementById('reset-confirm-modal');
  modal.style.display = 'flex';
}
// === 关闭弹窗 ===
function closeResetModal() {
  const modal = document.getElementById('reset-confirm-modal');
  modal.style.display = 'none';
}
// === 确认重置 (实际执行逻辑) ===
async function confirmReset() {
  closeResetModal(); // 关闭弹窗

  try {
    // 调用后端清空记忆
    await fetchWithAuth("/api/ai/clear-history", {method: "POST"});

    // 前端重置 UI
    const container = document.getElementById('chat-container');
    container.innerHTML = '';

    // 显示空状态图
    const emptyState = document.getElementById('ai-empty-state');
    if (emptyState) emptyState.classList.remove('d-none');

    // 隐藏底部输入栏和计数器
    document.getElementById('input-area').classList.add('d-none');
    document.getElementById('turn-badge').classList.add('d-none');

    // 重置变量
    turnCount = 0;
    updateTurnUI();
    updateStatusText("等待指令...");

    // 取消所有勾选的食材
    document.querySelectorAll('input[name="foodIds"]').forEach(cb => cb.checked = false);

  } catch (e) {
    console.error(e);
    alert("重置失败，请检查网络");
  }
}

// === 辅助函数 ===
function appendMessage(role, text) {
  const container = document.getElementById('chat-container');
  const row = document.createElement('div');
  row.className = `message-row ${role}`;

  let contentHtml = typeof marked !== 'undefined' ? marked.parse(text) : text.replace(/\n/g, '<br>');

  row.innerHTML = `
        <div class="bubble ${role}">
            ${contentHtml}
        </div>
    `;

  container.appendChild(row);
  container.scrollTop = container.scrollHeight;
}

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

function updateStatusText(text) {
  document.querySelector('.status-text').textContent = text;
}

function updateTurnUI() {
  document.getElementById('turn-count').innerText = turnCount;
  const input = document.getElementById('taste-input');
  const sendBtn = document.getElementById('btn-send');

  if (turnCount >= MAX_TURNS) {
    input.placeholder = "对话次数已用完，请开启新对话";
    input.disabled = true;
    sendBtn.disabled = true;
    document.getElementById('btn-new-chat').classList.remove('d-none');
  } else {
    input.placeholder = `口味微调 (剩余${MAX_TURNS - turnCount}次)`;
    input.disabled = false;
    sendBtn.disabled = false;
  }
}