let selectedFoodIds = new Set();
let configModal = null;
let feedbackModal = null;
let currentRecipeRecordId = null;
let currentMaskedApiKey = "";
let selectedFeedbackReason = "";

const token = localStorage.getItem("fg_token") || sessionStorage.getItem("fg_token");
if (!token) {
  alert("登录已过期，请重新登录");
  window.location.href = "/login";
}

const ENGINE_DEFAULTS = {
  DeepSeek: { url: "https://api.deepseek.com", model: "deepseek-chat" },
  Qwen: { url: "https://dashscope.aliyuncs.com/compatible-mode/v1", model: "qwen-plus" },
  OpenAI: { url: "https://api.openai.com/v1", model: "gpt-3.5-turbo" },
  Custom: { url: "", model: "" }
};

const AI_ERROR_MESSAGES = {
  AI_CONFIG_MISSING: "请先配置 AI 模型信息",
  AI_API_UNAUTHORIZED: "API Key 不可用，请检查 AI 配置",
  AI_API_TIMEOUT: "AI 服务响应超时，请稍后重试",
  AI_BAD_RESPONSE_FORMAT: "AI 返回格式异常，请重新生成",
  AI_EMPTY_RESPONSE: "AI 没有返回有效内容，请重新生成",
  AI_UNKNOWN_ERROR: "AI 服务暂时不可用，请稍后再试"
};

document.addEventListener("DOMContentLoaded", () => {
  configModal = new bootstrap.Modal(document.getElementById("aiConfigModal"));
  feedbackModal = new bootstrap.Modal(document.getElementById("feedbackReasonModal"));
  bindFeedbackReasonPicker();
  loadFoodList();
  checkConfigStatus();

  const searchInput = document.getElementById("foodSearch");
  if (searchInput) {
    searchInput.addEventListener("input", (event) => filterFoods(event.target.value));
  }
});

async function fetchWithAuth(url, options = {}) {
  const headers = {
    Authorization: token,
    ...options.headers
  };

  const response = await fetch(url, { ...options, headers });
  if (response.status === 401) {
    alert("登录已过期，请重新登录");
    localStorage.removeItem("fg_token");
    sessionStorage.removeItem("fg_token");
    window.location.href = "/login";
    throw new Error("Unauthorized");
  }
  return response;
}

async function loadFoodList() {
  const container = document.getElementById("foodList");

  try {
    const response = await fetchWithAuth("/api/food/list");
    const result = await response.json();

    if (result.code === 200) {
      if (!result.data || result.data.length === 0) {
        container.innerHTML = `
          <div class="text-center py-4 text-muted">
            <i class="bi bi-box2 display-6 opacity-50"></i>
            <p class="mt-2 small">冰箱还是空的<br>先去 <a href="/food/add-page">录入食材</a></p>
          </div>
        `;
      } else {
        renderFoods(result.data);
      }
      return;
    }

    container.innerHTML = `<div class="text-center py-4 text-danger small">${result.message || "加载失败"}</div>`;
  } catch (error) {
    console.error("加载食材失败:", error);
    container.innerHTML = '<div class="text-center py-4 text-danger small">网络连接失败</div>';
  }
}

function renderFoods(foods) {
  const container = document.getElementById("foodList");
  container.innerHTML = "";

  foods.forEach((food) => {
    const item = document.createElement("div");
    item.className = "list-group-item bg-transparent border-0 border-bottom d-flex justify-content-between align-items-center py-3 px-2";
    item.innerHTML = `
      <div class="form-check m-0">
        <input class="form-check-input cursor-pointer" type="checkbox" value="${food.id}"
               id="check-${food.id}" onchange="toggleFood(this)">
        <label class="form-check-label cursor-pointer ms-2 fw-medium text-dark" for="check-${food.id}">
          ${food.name}
        </label>
      </div>
      <span class="badge bg-white text-dark shadow-sm border rounded-pill px-3">
        ${food.quantity} ${food.unit || ""}
      </span>
    `;
    container.appendChild(item);
  });
}

function toggleFood(checkbox) {
  const id = Number.parseInt(checkbox.value, 10);
  if (checkbox.checked) {
    selectedFoodIds.add(id);
  } else {
    selectedFoodIds.delete(id);
  }
}

async function checkConfigStatus() {
  try {
    const response = await fetchWithAuth("/api/ai/my-config");
    const result = await response.json();
    if (result.code !== 200) {
      return;
    }

    if (!result.data) {
      updateDefaultConfig();
      setTimeout(() => configModal.show(), 800);
      return;
    }
    fillConfigForm(result.data);
  } catch (error) {
    console.warn("AI 配置检查失败:", error);
  }
}

function fillConfigForm(config) {
  currentMaskedApiKey = config.apiKeyMasked || "";
  document.getElementById("config-key").value = config.apiKey || "";
  document.getElementById("config-url").value = config.baseUrl || "";
  document.getElementById("config-model").value = config.model || "";
  document.getElementById("config-taste").value = config.tastePreference || "";
  document.getElementById("config-goal").value = config.dietGoal || "";
  document.getElementById("config-taboos").value = config.taboos || "";
  document.getElementById("config-time").value = config.cookingTimePreference || "";

  const url = (config.baseUrl || "").toLowerCase();
  const select = document.getElementById("config-engine");
  if (url.includes("deepseek")) {
    select.value = "DeepSeek";
  } else if (url.includes("aliyun") || url.includes("dashscope")) {
    select.value = "Qwen";
  } else if (url.includes("openai")) {
    select.value = "OpenAI";
  } else {
    select.value = "Custom";
  }
}

function updateDefaultConfig() {
  const engine = document.getElementById("config-engine").value;
  const defaults = ENGINE_DEFAULTS[engine];
  if (!defaults) {
    return;
  }
  document.getElementById("config-url").value = defaults.url;
  document.getElementById("config-model").value = defaults.model;
}

function openConfigModal() {
  configModal.show();
}

async function saveAiConfig() {
  const apiKeyInput = document.getElementById("config-key").value.trim();
  const config = {
    apiKey: apiKeyInput || currentMaskedApiKey,
    baseUrl: document.getElementById("config-url").value.trim(),
    model: document.getElementById("config-model").value.trim(),
    tastePreference: document.getElementById("config-taste").value,
    dietGoal: document.getElementById("config-goal").value,
    taboos: document.getElementById("config-taboos").value.trim(),
    cookingTimePreference: document.getElementById("config-time").value
  };

  if (!config.apiKey || !config.baseUrl || !config.model) {
    alert("请完整填写 API Key、URL 和模型名");
    return;
  }

  const button = document.querySelector("#configForm button");
  const originalText = button.innerHTML;
  button.innerHTML = "保存中...";
  button.disabled = true;

  try {
    const response = await fetchWithAuth("/api/ai/config", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(config)
    });
    const result = await response.json();

    if (result.code === 200) {
      configModal.hide();
      currentMaskedApiKey = apiKeyInput ? "" : currentMaskedApiKey;
      alert("配置已保存");
      await checkConfigStatus();
      return;
    }
    alert(formatAiError(result));
  } catch (error) {
    alert("网络错误，请稍后再试");
  } finally {
    button.innerHTML = originalText;
    button.disabled = false;
  }
}

async function generateRecipe() {
  await requestRecipeGeneration(Array.from(selectedFoodIds));
}

async function regenerateRecipe() {
  if (selectedFoodIds.size === 0) {
    alert("请先选择食材后再重新生成");
    return;
  }
  await requestRecipeGeneration(Array.from(selectedFoodIds));
}

async function requestRecipeGeneration(foodIds) {
  if (!foodIds || foodIds.length === 0) {
    alert("请先在左侧列表选择至少一种食材");
    return;
  }

  currentRecipeRecordId = null;
  updateFeedbackHint("");
  resetNegativeFeedbackDraft();

  const chatBox = document.getElementById("chatHistory");
  chatBox.innerHTML = "";

  const loadingId = `loading-${Date.now()}`;
  chatBox.insertAdjacentHTML("beforeend", `
    <div id="${loadingId}" class="d-flex mb-3 justify-content-start animate-enter">
      <div class="p-3 rounded-3 shadow-sm bg-white border">
        <div class="spinner-grow spinner-grow-sm text-primary" role="status"></div>
        <span class="ms-2 small text-muted">AI 正在整理菜谱...</span>
      </div>
    </div>
  `);
  chatBox.scrollTop = chatBox.scrollHeight;

  try {
    const response = await fetchWithAuth("/api/ai/generate-recipe", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(foodIds)
    });
    const result = await response.json();
    document.getElementById(loadingId)?.remove();

    if (result.code === 200) {
      currentRecipeRecordId = result.data.recipeRecordId || null;
      renderRecipeSummary(result.data.structured);
      const content = result.data.markdown || result.data.content || "";
      appendMessage("assistant", content);
      setAiInteractionVisible(true);
      return;
    }

    if (result.message === "AI_CONFIG_MISSING") {
      configModal.show();
    }
    appendMessage("assistant", `提示：${formatAiError(result)}`);
  } catch (error) {
    document.getElementById(loadingId)?.remove();
    appendMessage("assistant", "网络请求失败，请稍后重试");
  }
}

async function sendMessage() {
  const input = document.getElementById("userMessage");
  const message = input.value.trim();
  if (!message) {
    return;
  }

  appendMessage("user", message);
  input.value = "";

  try {
    const response = await fetchWithAuth("/api/ai/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ message })
    });
    const result = await response.json();

    if (result.code === 200) {
      appendMessage("assistant", result.data);
      return;
    }
    appendMessage("assistant", `提示：${formatAiError(result)}`);
  } catch (error) {
    appendMessage("assistant", "网络连接失败");
  }
}

function sendQuickPrompt(promptText) {
  const input = document.getElementById("userMessage");
  input.value = promptText;
  sendMessage();
}

function appendMessage(role, text) {
  const box = document.getElementById("chatHistory");
  const wrapper = document.createElement("div");
  wrapper.className = `d-flex mb-3 animate-enter ${role === "user" ? "justify-content-end" : "justify-content-start"}`;

  const bubble = document.createElement("div");
  bubble.className = `p-3 rounded-3 shadow-sm ${role === "user" ? "bg-gradient-blue text-white" : "bg-white border"}`;
  bubble.style.maxWidth = "85%";
  bubble.innerHTML = role === "user" ? escapeHtml(text) : marked.parse(text);

  wrapper.appendChild(bubble);
  box.appendChild(wrapper);
  box.scrollTop = box.scrollHeight;
}

function renderRecipeSummary(recipe) {
  if (!recipe) {
    return;
  }

  const box = document.getElementById("chatHistory");
  const summary = document.createElement("div");
  summary.className = "recipe-summary-card animate-enter mb-3";

  const difficultyMap = {
    easy: "简单",
    medium: "中等",
    hard: "进阶"
  };

  const chips = [
    recipe.estimatedTimeMinutes ? `${recipe.estimatedTimeMinutes} 分钟` : "时长待定",
    difficultyMap[recipe.difficulty] || "简单",
    recipe.useExpiringFoodFirst ? "优先消耗临期食材" : "常规搭配"
  ];

  summary.innerHTML = `
    <div class="recipe-summary-head">
      <div>
        <div class="recipe-summary-title">${escapeHtml(recipe.title || "AI 生成菜谱")}</div>
        <div class="recipe-summary-text">${escapeHtml(recipe.summary || "已按当前库存生成一份可执行菜谱")}</div>
      </div>
      <i class="bi bi-stars recipe-summary-icon"></i>
    </div>
    <div class="recipe-summary-chips">
      ${chips.map((chip) => `<span class="recipe-chip">${escapeHtml(chip)}</span>`).join("")}
    </div>
  `;

  box.appendChild(summary);
  box.scrollTop = box.scrollHeight;
}

async function submitRecipeFeedback(feedbackStatus, feedbackReason = "") {
  if (!currentRecipeRecordId) {
    updateFeedbackHint("当前菜谱还没有可反馈的记录");
    return;
  }

  try {
    const response = await fetchWithAuth("/api/ai/recipe-feedback", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        recipeRecordId: String(currentRecipeRecordId),
        feedbackStatus,
        feedbackReason
      })
    });
    const result = await response.json();

    if (result.code === 200) {
      updateFeedbackHint(
        feedbackStatus === "HELPFUL"
          ? "已记录你的正向反馈"
          : "已记录你的反馈，后续会用于优化推荐"
      );
      return;
    }
    updateFeedbackHint(formatAiError(result));
  } catch (error) {
    updateFeedbackHint("反馈提交失败，请稍后再试");
  }
}

function openFeedbackModal() {
  if (!currentRecipeRecordId) {
    updateFeedbackHint("当前菜谱还没有可反馈的记录");
    return;
  }
  resetNegativeFeedbackDraft();
  feedbackModal.show();
}

function bindFeedbackReasonPicker() {
  const buttons = document.querySelectorAll(".feedback-reason-pill");
  buttons.forEach((button) => {
    button.addEventListener("click", () => {
      document.querySelectorAll(".feedback-reason-pill").forEach((pill) => pill.classList.remove("active"));
      button.classList.add("active");
      selectedFeedbackReason = button.dataset.reason || "";
    });
  });
}

async function submitNegativeFeedback() {
  const note = document.getElementById("feedbackReasonInput").value.trim();
  const finalReason = [selectedFeedbackReason, note].filter(Boolean).join("；");
  await submitRecipeFeedback("NOT_HELPFUL", finalReason);
  feedbackModal.hide();
}

function resetNegativeFeedbackDraft() {
  selectedFeedbackReason = "";
  document.getElementById("feedbackReasonInput").value = "";
  document.querySelectorAll(".feedback-reason-pill").forEach((pill) => pill.classList.remove("active"));
}

function updateFeedbackHint(message) {
  const hint = document.getElementById("feedbackHint");
  if (hint) {
    hint.textContent = message || "";
  }
}

async function clearHistory() {
  if (!confirm("确定要清除当前对话记忆吗？")) {
    return;
  }

  try {
    await fetchWithAuth("/api/ai/clear-history", { method: "POST" });
    currentRecipeRecordId = null;
    document.getElementById("chatHistory").innerHTML = `
      <div class="text-center text-muted mt-5 opacity-75">
        <i class="bi bi-stars fs-1 d-block mb-3 text-warning"></i>
        <p>记忆已清除，准备开始新的菜谱探索吧</p>
      </div>
    `;
    setAiInteractionVisible(false);
    updateFeedbackHint("");
  } catch (error) {
    alert("清除失败，请稍后再试");
  }
}

function filterFoods(keyword) {
  const normalizedKeyword = keyword.toLowerCase();
  const items = document.querySelectorAll("#foodList .list-group-item");
  items.forEach((item) => {
    const text = item.innerText.toLowerCase();
    item.style.display = text.includes(normalizedKeyword) ? "flex" : "none";
  });
}

function formatAiError(result) {
  if (!result) {
    return AI_ERROR_MESSAGES.AI_UNKNOWN_ERROR;
  }
  if (result.data && typeof result.data === "string") {
    return result.data;
  }
  return AI_ERROR_MESSAGES[result.message] || result.message || AI_ERROR_MESSAGES.AI_UNKNOWN_ERROR;
}

function setAiInteractionVisible(visible) {
  document.getElementById("inputArea").style.display = visible ? "block" : "none";
  document.getElementById("quickActions").style.display = visible ? "block" : "none";
  document.getElementById("feedbackPanel").style.display = visible ? "block" : "none";
  document.getElementById("regenerateButton").style.display = visible ? "block" : "none";
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#39;");
}
