const API_URL = "http://localhost:3000/usuarios";
const FOTO_PADRAO = "../../assets/imagens/anonimo.png";

let senhaOriginal = "";
let dadosUsuarioCompletos = null;

const apiRequest = async (url, method, data = null) => {
  try {
    const options = {
      method,
      headers: { "Content-Type": "application/json" },
    };
    if (data) options.body = JSON.stringify(data);
    const response = await fetch(url, options);
    if (!response.ok) throw new Error(`Erro HTTP: ${response.status}`);
    return await response.json();
  } catch (error) {
    console.error("Erro na requisição:", error);
    displayMessage(`Erro ao comunicar com o servidor: ${error.message}`, 'error');
    return null;
  }
};

const sanitizeInput = (value) => (value ? value.toString().trim() : "");

const converterParaBase64 = (file) =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });

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

const validateLogin = (login) => {
  if (!login) {
    return { valid: false, message: "O campo de login não pode estar vazio.", field: "login" };
  }
  if (login.length < 5) {
    return { valid: false, message: "O login deve ter no mínimo 5 caracteres.", field: "login" };
  }
  if (!/^[a-zA-Z0-9_]+$/.test(login)) {
    return { valid: false, message: "O login deve conter apenas letras, números ou sublinhado (_).", field: "login" };
  }
  return { valid: true };
};

const validatePassword = (senha) => {
  if (!senha) {
    return { valid: false, message: "O campo de senha não pode estar vazio.", field: "senha" };
  }
  if (senha.length < 8) {
    return { valid: false, message: "A senha deve ter no mínimo 8 caracteres.", field: "senha" };
  }
  if (!/[A-Z]/.test(senha)) {
    return { valid: false, message: "A senha deve conter pelo menos uma letra maiúscula.", field: "senha" };
  }
  if (!/[a-z]/.test(senha)) {
    return { valid: false, message: "A senha deve conter pelo menos uma letra minúscula.", field: "senha" };
  }
  if (!/[0-9]/.test(senha)) {
    return { valid: false, message: "A senha deve conter pelo menos um número.", field: "senha" };
  }
  if (!/[!@#$%^&*]/.test(senha)) {
    return { valid: false, message: "A senha deve conter pelo menos um caractere especial (!@#$%^&*).", field: "senha" };
  }
  return { valid: true };
};

const validateEmail = (email) => {
  if (!email) {
    return { valid: false, message: "O campo de email não pode estar vazio.", field: "email" };
  }
  const emailRegex = /^[^\s@]+@[^\s@]+\.(com|net|org|edu|gov|br)$/;
  if (!emailRegex.test(email)) {
    return { valid: false, message: "O email deve ser válido (ex.: usuario@dominio.com).", field: "email" };
  }
  return { valid: true };
};

const validateNome = (nome) => {
  if (!nome) {
    return { valid: false, message: "O campo de nome não pode estar vazio.", field: "nome" };
  }
  if (nome.length < 3) {
    return { valid: false, message: "O nome completo deve ter no mínimo 3 caracteres.", field: "nome" };
  }
  return { valid: true };
};

const carregarDadosUsuario = async () => {
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
    saudacao.textContent = `${usuarioCorrente.nome || "usuário"}, visualize suas informações pessoais`;
  }

  try {
    const usuario = await apiRequest(`${API_URL}/${usuarioCorrente.id}`, "GET");
    if (!usuario) throw new Error("Usuário não encontrado");
    dadosUsuarioCompletos = usuario;
    senhaOriginal = usuario.senha || "";
    preencherFormulario(usuario);
  } catch (error) {
    console.error("Erro ao carregar dados:", error);
    displayMessage("Erro ao carregar dados do perfil: " + error.message, 'error');
  }
};

const preencherFormulario = (usuario) => {
  preencherCampo("nome", usuario.nome);
  preencherCampo("login", usuario.login);
  preencherCampo("email", usuario.email);
  preencherCampo("telefone", usuario.telefone);
  preencherCampo("localizacao", usuario.localizacao);
  preencherCampo("data_nasc", usuario.data_nasc);
  preencherCampo("habilidades", usuario.habilidades);
  preencherCampo("formacao", usuario.formacao);
  preencherCampo("empresa", usuario.empresa);
  preencherCampo("profissao", usuario.profissao);
  preencherCampo("idioma", usuario.idioma || "pt_BR");
  preencherCampoSenha(usuario.senha);
  carregarFoto(usuario.foto);
  carregarNotificacoes(usuario.notificacoes);
  atualizarCabecalho(usuario.nome, usuario.profissao);
};

const preencherCampo = (id, valor) => {
  const campo = document.getElementById(id);
  if (campo) campo.value = valor || "";
};

const preencherCampoSenha = (senha) => {
  const campoSenha = document.getElementById("senha");
  if (campoSenha) {
    if (senha && senha.trim() !== "") {
      campoSenha.value = "******";
      campoSenha.setAttribute("data-senha-original", senha);
    } else {
      campoSenha.value = "";
    }
  }
};

const limparSenhaAsteriscos = function () {
  if (this.value === "******") this.value = "";
};

const restaurarSenhaAsteriscos = function () {
  if (this.value.trim() === "") this.value = "******";
};

const carregarFoto = (fotoUrl) => {
  const fotoPerfil = document.getElementById("foto-perfil");
  if (fotoPerfil) {
    const urlFoto = fotoUrl && fotoUrl.trim() !== "" ? fotoUrl : FOTO_PADRAO;
    fotoPerfil.src = urlFoto;
    fotoPerfil.onerror = () => {
      fotoPerfil.src = FOTO_PADRAO;
    };
  }
};

const carregarNotificacoes = (notificacoes) => {
  if (notificacoes) {
    ["email", "relatorio", "metas"].forEach((tipo) => {
      const interruptor = document.getElementById(`notif-${tipo}`);
      if (interruptor && notificacoes[tipo]) interruptor.classList.add("active");
    });
  }
};

const atualizarCabecalho = (nome, profissao) => {
  document.querySelector(".nome-perfil").textContent = nome || "Nome não informado";
  document.querySelector(".cargo-perfil").textContent = profissao || "Profissão não informada";
};

const salvarPerfil = async (event) => {
  event.preventDefault();
  const salvarButton = document.getElementById("botao-salvar");
  salvarButton.disabled = true;
  limparErros();
  const dadosFormulario = coletarDadosFormulario();
  if (!validarFormulario(dadosFormulario)) {
    displayMessage("Corrija os erros no formulário antes de salvar.", 'error');
    salvarButton.disabled = false;
    return;
  }

  const usuarioCorrente = JSON.parse(sessionStorage.getItem("usuarioCorrente"));
  const usuarios = await apiRequest(API_URL, "GET");
  if (usuarios) {
    const loginExists = usuarios.some(u => u.id !== usuarioCorrente.id && u.login === dadosFormulario.login);
    const emailExists = usuarios.some(u => u.id !== usuarioCorrente.id && u.email === dadosFormulario.email);
    if (loginExists) {
      mostrarErro("login", "Este login já está em uso. Por favor, escolha outro.");
      salvarButton.disabled = false;
      return;
    }
    if (emailExists) {
      mostrarErro("email", "Este email já está em uso. Por favor, escolha outro.");
      salvarButton.disabled = false;
      return;
    }
  }

  const dadosAtualizados = {
    ...dadosUsuarioCompletos,
    ...dadosFormulario,
    id: usuarioCorrente.id,
  };

  const resultado = await apiRequest(`${API_URL}/${usuarioCorrente.id}`, "PUT", dadosAtualizados);
  if (resultado) {
    dadosUsuarioCompletos = resultado;
    senhaOriginal = resultado.senha;
    sessionStorage.setItem("usuarioCorrente", JSON.stringify({
      id: resultado.id,
      login: resultado.login,
      email: resultado.email,
      nome: resultado.nome,
    }));
    displayMessage("Perfil salvo com sucesso!", 'success');
    await carregarDadosUsuario();
    setTimeout(() => {
      window.location.href = '../../index.html';
    }, 1000);
  }
  salvarButton.disabled = false;
};

const coletarDadosFormulario = () => {
  const campoSenha = document.getElementById("senha");
  let senhaFinal = senhaOriginal;
  if (campoSenha && campoSenha.value.trim() !== "" && campoSenha.value !== "******") {
    senhaFinal = campoSenha.value.trim();
  }
  const notificacoes = {
    email: document.getElementById("notif-email")?.classList.contains("active") || false,
    relatorio: document.getElementById("notif-relatorio")?.classList.contains("active") || false,
    metas: document.getElementById("notif-metas")?.classList.contains("active") || false,
  };
  return {
    login: sanitizeInput(document.getElementById("login")?.value),
    senha: senhaFinal,
    nome: sanitizeInput(document.getElementById("nome")?.value),
    email: sanitizeInput(document.getElementById("email")?.value),
    telefone: sanitizeInput(document.getElementById("telefone")?.value),
    data_nasc: document.getElementById("data_nasc")?.value || "",
    localizacao: sanitizeInput(document.getElementById("localizacao")?.value),
    profissao: sanitizeInput(document.getElementById("profissao")?.value),
    formacao: sanitizeInput(document.getElementById("formacao")?.value),
    empresa: sanitizeInput(document.getElementById("empresa")?.value),
    habilidades: sanitizeInput(document.getElementById("habilidades")?.value),
    idioma: document.getElementById("idioma")?.value || "pt_BR",
    foto: document.getElementById("foto-perfil")?.src || FOTO_PADRAO,
    notificacoes,
  };
};

const validarFormulario = (dados) => {
  let isValid = true;
  if (!validarCampo("nome", dados.nome)) isValid = false;
  if (!validarCampo("email", dados.email)) isValid = false;
  if (!validarCampo("login", dados.login)) isValid = false;
  if (dados.telefone && !validarCampo("telefone", dados.telefone)) isValid = false;
  if (dados.senha && dados.senha !== senhaOriginal && !validarCampo("senha", dados.senha)) isValid = false;
  return isValid;
};

const validarCampo = (campo, valor) => {
  const erroDiv = document.getElementById(`erro-${campo}`);
  const inputElement = document.getElementById(campo);
  if (erroDiv) erroDiv.style.display = "none";
  if (inputElement) inputElement.classList.remove("invalid");

  switch (campo) {
    case "nome":
      const nomeValidation = validateNome(valor);
      if (!nomeValidation.valid) {
        mostrarErro(campo, nomeValidation.message);
        return false;
      }
      break;
    case "email":
      const emailValidation = validateEmail(valor);
      if (!emailValidation.valid) {
        mostrarErro(campo, emailValidation.message);
        return false;
      }
      break;
    case "login":
      const loginValidation = validateLogin(valor);
      if (!loginValidation.valid) {
        mostrarErro(campo, loginValidation.message);
        return false;
      }
      break;
    case "senha":
      if (valor && valor !== "******") {
        const passwordValidation = validatePassword(valor);
        if (!passwordValidation.valid) {
          mostrarErro(campo, passwordValidation.message);
          return false;
        }
      }
      break;
    case "telefone":
      if (valor && !/^\(?\d{2}\)?\s?\d{4,5}-?\d{4}$/.test(valor)) {
        mostrarErro(campo, "Telefone inválido (ex.: (31) 99999-9999)");
        return false;
      }
      break;
  }
  return true;
};

const mostrarErro = (campo, mensagem) => {
  const erroDiv = document.getElementById(`erro-${campo}`);
  const inputElement = document.getElementById(campo);
  if (erroDiv) {
    erroDiv.textContent = mensagem;
    erroDiv.style.display = "block";
  }
  if (inputElement) inputElement.classList.add("invalid");
};

const limparErros = () => {
  document.querySelectorAll(".mensagem-erro").forEach((erro) => (erro.style.display = "none"));
  document.querySelectorAll(".invalid").forEach((campo) => campo.classList.remove("invalid"));
};

const alterarFoto = async (event) => {
  const file = event.target.files[0];
  if (!file || !file.type.startsWith("image/") || file.size > 5 * 1024 * 1024) {
    displayMessage("Selecione uma imagem válida (máx. 5MB).", 'error');
    return;
  }
  try {
    const base64 = await converterParaBase64(file);
    document.getElementById("foto-perfil").src = base64;
  } catch (error) {
    displayMessage("Erro ao processar a imagem.", 'error');
  }
};

document.addEventListener("DOMContentLoaded", () => {
  document.getElementById("formulario").addEventListener("submit", salvarPerfil);
  document.getElementById("botao-cancelar").addEventListener("click", () => {
    if (confirm("Deseja cancelar? Alterações não salvas serão perdidas!")) {
      window.location.href = "../../index.html";
    }
  });
  document.getElementById("botao-limpar").addEventListener("click", () => {
    if (confirm("Deseja limpar todos os campos?")) {
      document.getElementById("formulario").reset();
      limparErros();
      document.querySelectorAll(".interruptor").forEach((i) => i.classList.remove("active"));
      document.getElementById("foto-perfil").src = FOTO_PADRAO;
    }
  });
  document.getElementById("botao-editar-foto").addEventListener("click", () =>
    document.getElementById("input-foto").click()
  );
  document.getElementById("input-foto").addEventListener("change", alterarFoto);
  document.getElementById("senha").addEventListener("focus", limparSenhaAsteriscos);
  document.getElementById("senha").addEventListener("blur", restaurarSenhaAsteriscos);
  document.getElementById("nome").addEventListener("input", () => {
    document.querySelector(".nome-perfil").textContent =
      document.getElementById("nome").value || "Nome não informado";
  });
  document.getElementById("profissao").addEventListener("input", () => {
    document.querySelector(".cargo-perfil").textContent =
      document.getElementById("profissao").value || "Profissão não informada";
  });
  document.querySelectorAll(".interruptor").forEach((interruptor) =>
    interruptor.addEventListener("click", () => interruptor.classList.toggle("active"))
  );
  ["nome", "email", "login", "telefone", "senha"].forEach((campo) => {
    const input = document.getElementById(campo);
    if (input) input.addEventListener("blur", () => validarCampo(campo, input.value.trim()));
  });

  carregarDadosUsuario();
});