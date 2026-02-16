// === 全局变量与配置 ===
let selectedFoodIds = new Set();
let configModal = null;

// 1. 获取 Token (核心修复：如果没有 Token，直接跳回登录页)
const token = localStorage.getItem("fg_token") || sessionStorage.getItem("fg_token");
if (!token) {
  alert("登录已过期，请重新登录");
  window.location.href = "/login";
}

// 厂商默认配置字典
const ENGINE_DEFAULTS = {
  'DeepSeek': { url: 'https://api.deepseek.com', model: 'deepseek-chat' },
  'Qwen': { url: 'https://dashscope.aliyuncs.com/compatible-mode/v1', model: 'qwen-plus' },
  'OpenAI': { url: 'https://api.openai.com/v1', model: 'gpt-3.5-turbo' },
  'Custom': { url: '', model: '' }
};

document.addEventListener('DOMContentLoaded', () => {
  configModal = new bootstrap.Modal(document.getElementById('aiConfigModal'));

  // 2. 加载食材列表
  loadFoodList();

  // 3. 检查配置
  checkConfigStatus();

  // 绑定搜索事件
  const searchInput = document.getElementById('foodSearch');
  if (searchInput) {
    searchInput.addEventListener('input', (e) => filterFoods(e.target.value));
  }
});

// === 通用 Fetch 封装 (自动带 Token) ===
async function fetchWithAuth(url, options = {}) {
  // 默认 Header
  const headers = {
    'Authorization': token,
    ...options.headers // 合并用户传入的 header (如 Content-Type)
  };

  const res = await fetch(url, { ...options, headers });

  // 处理 Token 过期
  if (res.status === 401) {
    alert("登录已过期，请重新登录");
    localStorage.removeItem("fg_token");
    window.location.href = "/login";
    throw new Error("Unauthorized");
  }
  return res;
}

// === 业务逻辑 ===

async function loadFoodList() {
  const container = document.getElementById('foodList');

  try {
    // 使用 fetchWithAuth 替代 fetch
    const res = await fetchWithAuth('/api/food/list');
    const result = await res.json();

    if (result.code === 200) {
      if (!result.data || result.data.length === 0) {
        container.innerHTML = `
                    <div class="text-center py-4 text-muted">
                        <i class="bi bi-box2 display-6 opacity-50"></i>
                        <p class="mt-2 small">冰箱空空如也<br>去<a href="/food/add-page">录入食材</a>吧</p>
                    </div>
                `;
      } else {
        renderFoods(result.data);
      }
    } else {
      console.error("加载食材失败:", result);
      container.innerHTML = `<div class="text-center py-4 text-danger small">${result.message || '加载失败'}</div>`;
    }
  } catch (e) {
    console.error("请求异常:", e);
    container.innerHTML = `<div class="text-center py-4 text-danger small">网络连接失败</div>`;
  }
}

function renderFoods(foods) {
  const container = document.getElementById('foodList');
  container.innerHTML = '';

  foods.forEach(food => {
    const item = document.createElement('div');
    item.className = 'list-group-item bg-transparent border-0 border-bottom d-flex justify-content-between align-items-center py-3 px-2';
    item.innerHTML = `
            <div class="form-check m-0">
                <input class="form-check-input cursor-pointer" type="checkbox" value="${food.id}" 
                       id="check-${food.id}"
                       onchange="toggleFood(this, '${food.name}')">
                <label class="form-check-label cursor-pointer ms-2 fw-medium text-dark" for="check-${food.id}">
                    ${food.name}
                </label>
            </div>
            <span class="badge bg-white text-dark shadow-sm border rounded-pill px-3">
                ${food.quantity} ${food.unit || ''}
            </span>
        `;
    container.appendChild(item);
  });
}

function toggleFood(checkbox, name) {
  if (checkbox.checked) {
    selectedFoodIds.add(parseInt(checkbox.value));
  } else {
    selectedFoodIds.delete(parseInt(checkbox.value));
  }
}

// === AI 配置相关 ===

async function checkConfigStatus() {
  try {
    const res = await fetchWithAuth('/api/ai/my-config');
    if(res.ok) {
      const result = await res.json();
      if (result.code === 200) {
        if (!result.data) {
          // 未配置
          updateDefaultConfig();
          setTimeout(() => configModal.show(), 800);
        } else {
          // 已配置，回显
          fillConfigForm(result.data);
        }
      }
    }
  } catch (e) {
    console.warn("AI 配置检查跳过", e);
  }
}

function fillConfigForm(config) {
  document.getElementById('config-key').value = config.apiKey || '';
  document.getElementById('config-url').value = config.baseUrl || '';
  document.getElementById('config-model').value = config.model || '';

  const url = (config.baseUrl || '').toLowerCase();
  const select = document.getElementById('config-engine');

  if(url.includes('deepseek')) select.value = 'DeepSeek';
  else if(url.includes('aliyun') || url.includes('dashscope')) select.value = 'Qwen';
  else if(url.includes('openai')) select.value = 'OpenAI';
  else select.value = 'Custom';
}

function updateDefaultConfig() {
  const engine = document.getElementById('config-engine').value;
  const defaults = ENGINE_DEFAULTS[engine];
  if (defaults) {
    document.getElementById('config-url').value = defaults.url;
    document.getElementById('config-model').value = defaults.model;
  }
}

function openConfigModal() {
  configModal.show();
}

async function saveAiConfig() {
  const config = {
    apiKey: document.getElementById('config-key').value.trim(),
    baseUrl: document.getElementById('config-url').value.trim(),
    model: document.getElementById('config-model').value.trim()
  };

  if (!config.apiKey || !config.baseUrl) {
    alert("请完整填写 API Key 和 URL");
    return;
  }

  const btn = document.querySelector('#configForm button');
  const originalText = btn.innerHTML;
  btn.innerHTML = '保存中...';
  btn.disabled = true;

  try {
    const res = await fetchWithAuth('/api/ai/config', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(config)
    });
    const result = await res.json();

    if (result.code === 200) {
      configModal.hide();
      alert("配置已保存！");
    } else {
      alert(result.message || "保存失败");
    }
  } catch (e) {
    alert("网络错误");
  } finally {
    btn.innerHTML = originalText;
    btn.disabled = false;
  }
}

// === 生成食谱与对话 ===

async function generateRecipe() {
  if (selectedFoodIds.size === 0) {
    alert("请先在左侧列表选择至少一种食材！");
    return;
  }

  const chatBox = document.getElementById('chatHistory');
  const loadingId = 'loading-' + Date.now();

  // 清空初始提示
  if (chatBox.querySelector('.text-center')) chatBox.innerHTML = '';

  const loadingHtml = `
        <div id="${loadingId}" class="d-flex mb-3 justify-content-start animate-enter">
            <div class="p-3 rounded-3 shadow-sm bg-white border">
                <div class="spinner-grow spinner-grow-sm text-primary" role="status"></div>
                <span class="ms-2 small text-muted">AI 正在构思食谱...</span>
            </div>
        </div>
    `;
  chatBox.insertAdjacentHTML('beforeend', loadingHtml);
  chatBox.scrollTop = chatBox.scrollHeight;

  try {
    const res = await fetchWithAuth('/api/ai/generate-recipe', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(Array.from(selectedFoodIds))
    });
    const result = await res.json();

    document.getElementById(loadingId)?.remove();

    if (result.code === 200) {
      appendMessage('assistant', result.data.content); // 注意：这里根据后端返回结构可能需要调整，假设返回的是 ChatResponse 对象
      document.getElementById('inputArea').style.display = 'block';
    } else if (result.message === "AI_CONFIG_MISSING") {
      alert("请先配置 AI 模型信息！");
      configModal.show();
    } else {
      appendMessage('assistant', `⚠️ 生成失败：${result.message}`);
    }
  } catch (e) {
    document.getElementById(loadingId)?.remove();
    appendMessage('assistant', `❌ 网络请求出错：${e.message}`);
  }
}

async function sendMessage() {
  const input = document.getElementById('userMessage');
  const msg = input.value.trim();
  if (!msg) return;

  appendMessage('user', msg);
  input.value = '';

  try {
    const res = await fetchWithAuth('/api/ai/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: msg })
    });
    const result = await res.json();

    if (result.code === 200) {
      appendMessage('assistant', result.data);
    } else {
      appendMessage('assistant', `⚠️ 错误: ${result.message}`);
    }
  } catch (e) {
    appendMessage('assistant', "❌ 网络连接失败");
  }
}

function appendMessage(role, text) {
  const box = document.getElementById('chatHistory');
  const div = document.createElement('div');
  div.className = `d-flex mb-3 animate-enter ${role === 'user' ? 'justify-content-end' : 'justify-content-start'}`;

  const bubble = document.createElement('div');
  bubble.className = `p-3 rounded-3 shadow-sm ${role === 'user' ? 'bg-gradient-blue text-white' : 'bg-white border'}`;
  bubble.style.maxWidth = "85%";

  // 使用 marked 解析
  bubble.innerHTML = role === 'user' ? text : marked.parse(text);

  div.appendChild(bubble);
  box.appendChild(div);
  box.scrollTop = box.scrollHeight;
}

async function clearHistory() {
  if(!confirm('确定要清除当前的对话记忆吗？')) return;

  try {
    await fetchWithAuth('/api/ai/clear-history', { method: 'POST' });
    document.getElementById('chatHistory').innerHTML = `
            <div class="text-center text-muted mt-5 opacity-75">
                <i class="bi bi-stars fs-1 d-block mb-3 text-warning"></i>
                <p>记忆已清除，准备好开启新的美味探索了！</p>
            </div>
        `;
    document.getElementById('inputArea').style.display = 'none';
  } catch (e) {
    alert("清除失败");
  }
}

function filterFoods(keyword) {
  const items = document.querySelectorAll('#foodList .list-group-item');
  items.forEach(item => {
    const text = item.innerText.toLowerCase();
    item.style.display = text.includes(keyword.toLowerCase()) ? 'flex' : 'none';
  });
}