document.addEventListener("DOMContentLoaded", () => {
  const usuarioCorrenteJSON = sessionStorage.getItem("usuarioCorrente");
  if (!usuarioCorrenteJSON) {
    window.location.href = "../login/login.html";
    return;
  }

  const usuarioCorrente = JSON.parse(usuarioCorrenteJSON);
  if (!usuarioCorrente.id) {
    displayMessage("Erro nos dados do usuário. Faça login novamente.", 'error');
    sessionStorage.removeItem("usuarioCorrente");
    window.location.href = "../login/login.html";
    return;
  }

  const saudacao = document.getElementById("texto-subtitulo");
  if (saudacao) {
    saudacao.textContent = `${usuarioCorrente.nome || "usuário"}, compartilhe sua experiência conosco`;
  }

  const form = document.getElementById("formulario");
  const cancelarBtn = document.getElementById("cancelar");
  const limparBtn = document.getElementById("limpar");
  const registrarBtn = document.getElementById("registrar");
  const inputs = form.querySelectorAll("input, textarea, select");
  const API_URL = "http://localhost:3000/usuarios";

  const displayMessage = (message, type = 'error') => {
    const container = document.getElementById('notification-container');
    const notification = document.createElement('div');
    notification.className = `notification ${type} show`;
    notification.textContent = message;
    container.appendChild(notification);
    setTimeout(() => {
      notification.classList.remove('show');
      setTimeout(() => notification.remove(), 300);
    }, 3000);
  };

  const sanitizeInput = (value) => {
    const div = document.createElement("div");
    div.textContent = value;
    return div.innerHTML;
  };

  const apiRequest = async (url, method, data = null) => {
    try {
      const response = await fetch(url, {
        method,
        headers: { "Content-Type": "application/json" },
        body: data ? JSON.stringify(data) : null,
      });

      if (!response.ok) {
        throw new Error(`Erro HTTP: ${response.status}`);
      }

      return await response.json();
    } catch (error) {
      console.error("Erro na requisição:", error);
      displayMessage(`Erro ao comunicar com o servidor: ${error.message}`, 'error');
      return null;
    }
  };

  const campos = [
    {
      id: "data",
      key: "data_inicio",
      label: "Data de Início",
      regras: [
        { condicao: (v) => v, mensagem: 'O campo "Data de Início" é obrigatório.' },
        { condicao: (v) => !isNaN(new Date(v).getTime()), mensagem: "Data inválida." },
        { condicao: (v) => new Date(v) <= new Date(), mensagem: "A data não pode ser futura." },
      ],
    },
    {
      id: "atrasos",
      key: "adversidades",
      label: "Atrasos Iniciais",
      regras: [
        { condicao: (v) => v, mensagem: 'O campo "Atrasos Iniciais" é obrigatório.' },
        { condicao: (v) => v.length >= 5, mensagem: "Deve ter pelo menos 5 caracteres." },
      ],
    },
    {
      id: "atividades",
      key: "atividades",
      label: "Atividades Realizadas",
      regras: [
        { condicao: (v) => v, mensagem: 'O campo "Atividades Realizadas" é obrigatório.' },
        { condicao: (v) => v.length >= 5, mensagem: "Deve ter pelo menos 5 caracteres." },
      ],
    },
    {
      id: "dificuldades",
      key: "dificuldades",
      label: "Dificuldades Encontradas",
      regras: [
        { condicao: (v) => v, mensagem: 'O campo "Dificuldades Encontradas" é obrigatório.' },
        { condicao: (v) => v.length >= 5, mensagem: "Deve ter pelo menos 5 caracteres." },
      ],
    },
    {
      id: "beneficios",
      key: "beneficios",
      label: "Benefícios Obtidos",
      regras: [
        { condicao: (v) => v, mensagem: 'O campo "Benefícios Obtidos" é obrigatório.' },
        { condicao: (v) => v.length >= 5, mensagem: "Deve ter pelo menos 5 caracteres." },
      ],
    },
    {
      id: "tempo",
      key: "duracao",
      label: "Tempo de Duração",
      regras: [
        { condicao: (v) => v !== null && v !== "", mensagem: 'O campo "Tempo de Duração" é obrigatório.' },
        {
          condicao: (v) => !isNaN(v) && v >= 1 && v <= 365,
          mensagem: "Deve ser um número entre 1 e 365.",
        },
      ],
    },
    {
      id: "satisfacao",
      key: "satisfacao",
      label: "Nível de Satisfação",
      regras: [
        { condicao: (v) => v !== null && v !== "", mensagem: 'O campo "Nível de Satisfação" é obrigatório.' },
        {
          condicao: (v) => !isNaN(v) && v >= 1 && v <= 10,
          mensagem: "Deve ser um número entre 1 e 10.",
        },
      ],
    },
    {
      id: "observacoes",
      key: "observacoes_feedback",
      label: "Observações",
      regras: [
        { condicao: (v) => v, mensagem: 'O campo "Observações" é obrigatório.' },
        { condicao: (v) => v.length >= 5, mensagem: "Deve ter pelo menos 5 caracteres." },
      ],
    },
    {
      id: "metas",
      key: "metas",
      label: "Próximas Metas",
      regras: [
        { condicao: (v) => v, mensagem: 'O campo "Próximas Metas" é obrigatório.' },
        { condicao: (v) => v.length >= 5, mensagem: "Deve ter pelo menos 5 caracteres." },
      ],
    },
  ];

  const coletarDados = () => {
    return {
      data_inicio: document.getElementById("data").value,
      adversidades: sanitizeInput(document.getElementById("atrasos").value.trim()),
      atividades: sanitizeInput(document.getElementById("atividades").value.trim()),
      dificuldades: sanitizeInput(document.getElementById("dificuldades").value.trim()),
      beneficios: sanitizeInput(document.getElementById("beneficios").value.trim()),
      duracao: document.getElementById("tempo").value ? Number.parseInt(document.getElementById("tempo").value) : null,
      satisfacao: document.getElementById("satisfacao").value
        ? Number.parseInt(document.getElementById("satisfacao").value)
        : null,
      observacoes_feedback: sanitizeInput(document.getElementById("observacoes").value.trim()),
      metas: sanitizeInput(document.getElementById("metas").value.trim()),
    };
  };

  const exibirErro = (campoId, mensagem) => {
    const input = document.getElementById(campoId);
    const erroDiv = document.getElementById(`erro-${campoId}`);
    if (input && erroDiv) {
      input.classList.add("invalid");
      erroDiv.textContent = mensagem;
      erroDiv.style.display = "block";
    }
  };

  const limparErros = () => {
    inputs.forEach((input) => {
      input.classList.remove("invalid");
      const erroDiv = document.getElementById(`erro-${input.id}`);
      if (erroDiv) {
        erroDiv.style.display = "none";
        erroDiv.textContent = "";
      }
    });
  };

  const validarCampo = (campoId, valor, regras) => {
    for (const regra of regras) {
      if (!regra.condicao(valor)) {
        exibirErro(campoId, regra.mensagem);
        return false;
      }
    }
    const erroDiv = document.getElementById(`erro-${campoId}`);
    if (erroDiv) {
      erroDiv.style.display = "none";
    }
    return true;
  };

  const validarFormulario = (dados) => {
    let isValid = true;
    campos.forEach((campo) => {
      const valor = dados[campo.key];
      if (!validarCampo(campo.id, valor, campo.regras)) {
        isValid = false;
      }
    });
    return isValid;
  };

  const salvarFeedback = async (event) => {
    event.preventDefault();
    registrarBtn.disabled = true;
    limparErros();
    const dados = coletarDados();
    if (!validarFormulario(dados)) {
      displayMessage("Por favor, corrija os erros no formulário.", 'error');
      registrarBtn.disabled = false;
      return;
    }

    const usuario = await apiRequest(`${API_URL}/${usuarioCorrente.id}`, "GET");
    if (!usuario) {
      displayMessage("Usuário não encontrado.", 'error');
      registrarBtn.disabled = false;
      return;
    }

    if (!usuario.infos) usuario.infos = [{}];
    if (!usuario.infos[0].feedback) usuario.infos[0].feedback = [];
    const editIndex = form.dataset.editIndex;
    if (editIndex !== undefined) {
      usuario.infos[0].feedback[editIndex] = dados;
    } else {
      usuario.infos[0].feedback.push(dados);
    }

    const sucesso = await apiRequest(`${API_URL}/${usuarioCorrente.id}`, "PUT", usuario);
    if (sucesso) {
      displayMessage(
        editIndex !== undefined ? "Feedback atualizado com sucesso!" : "Feedback registrado com sucesso!",
        'success'
      );
      setTimeout(() => {
        window.location.href = "../../index.html";
      }, 1000);
    }
    registrarBtn.disabled = false;
  };

  window.editarFeedback = async (index) => {
    const usuario = await apiRequest(`${API_URL}/${usuarioCorrente.id}`, "GET");
    if (usuario?.infos?.[0]?.feedback?.[index]) {
      const fb = usuario.infos[0].feedback[index];
      document.getElementById("data").value = fb.data_inicio;
      document.getElementById("atrasos").value = fb.adversidades;
      document.getElementById("atividades").value = fb.atividades;
      document.getElementById("dificuldades").value = fb.dificuldades;
      document.getElementById("beneficios").value = fb.beneficios;
      document.getElementById("tempo").value = fb.duracao || "";
      document.getElementById("satisfacao").value = fb.satisfacao || "";
      document.getElementById("observacoes").value = fb.observacoes_feedback;
      document.getElementById("metas").value = fb.metas;
      form.dataset.editIndex = index;
      window.scrollTo({ top: 0, behavior: "smooth" });
    }
  };

  form.addEventListener("submit", salvarFeedback);
  cancelarBtn.addEventListener("click", () => {
    if (confirm("Deseja realmente cancelar? Todas as alterações não salvas serão perdidas!")) {
      limparErros();
      window.location.href = "../../index.html";
    }
  });
  limparBtn.addEventListener("click", () => {
    if (confirm("Deseja limpar todos os campos do formulário?")) {
      form.reset();
      limparErros();
      delete form.dataset.editIndex;
      displayMessage("Formulário limpo com sucesso!", 'success');
    }
  });
  inputs.forEach((input) => {
    input.addEventListener("input", () => {
      const campo = campos.find((c) => c.id === input.id);
      if (campo) {
        const valor =
          input.id === "tempo" || input.id === "satisfacao"
            ? input.value
              ? Number.parseInt(input.value)
              : null
            : input.value.trim();
        validarCampo(input.id, valor, campo.regras);
      }
    });
  });
});