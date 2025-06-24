const LOGIN_URL = "/modulos/login/login.html";
let RETURN_URL = "../../index.html";
const API_URL = "/usuarios";

var db_usuarios = {};
var usuarioCorrente = {};

function initLoginApp() {
  const pagina = window.location.pathname;
  if (pagina != LOGIN_URL) {
    sessionStorage.setItem("returnURL", pagina);
    RETURN_URL = pagina;

    const usuarioCorrenteJSON = sessionStorage.getItem("usuarioCorrente");
    if (usuarioCorrenteJSON) {
      usuarioCorrente = JSON.parse(usuarioCorrenteJSON);
    } else {
      window.location.href = LOGIN_URL;
    }

    document.addEventListener("DOMContentLoaded", () => {
      showUserInfo("userInfo");
    });
  } else {
    const returnURL = sessionStorage.getItem("returnURL");
    RETURN_URL = returnURL || RETURN_URL;

    carregarUsuarios(() => {
      console.log("Usuários carregados...");
    });
  }
}

function carregarUsuarios(callback) {
  fetch(API_URL)
    .then((response) => {
      if (!response.ok) {
        throw new Error("Erro na resposta da API");
      }
      return response.json();
    })
    .then((data) => {
      db_usuarios = data;
      callback();
    })
    .catch((error) => {
      console.error("Erro ao ler usuários via API JSONServer:", error);
      displayMessage("Erro ao carregar usuários. Tente novamente mais tarde.", 'error');
    });
}

function validateLogin(login) {
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
}

function validatePassword(senha) {
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
}

function validateEmail(email) {
  if (!email) {
    return { valid: false, message: "O campo de email não pode estar vazio.", field: "email" };
  }
  const emailRegex = /^[^\s@]+@[^\s@]+\.(com|net|org|edu|gov|br)$/;
  if (!emailRegex.test(email)) {
    return { valid: false, message: "O email deve ser válido (ex.: usuario@dominio.com).", field: "email" };
  }
  return { valid: true };
}

function validateNome(nome) {
  if (!nome) {
    return { valid: false, message: "O campo de nome não pode estar vazio.", field: "nome" };
  }
  if (nome.length < 3) {
    return { valid: false, message: "O nome completo deve ter no mínimo 3 caracteres.", field: "nome" };
  }
  return { valid: true };
}

function loginUser(login, senha) {
  const loginValidation = validateLogin(login);
  if (!loginValidation.valid) {
    return { success: false, message: loginValidation.message, field: loginValidation.field };
  }

  const passwordValidation = validatePassword(senha);
  if (!passwordValidation.valid) {
    return { success: false, message: passwordValidation.message, field: passwordValidation.field };
  }

  for (var i = 0; i < db_usuarios.length; i++) {
    var usuario = db_usuarios[i];
    if (login === usuario.login) {
      if (senha === usuario.senha) {
        usuarioCorrente = { ...usuario };
        sessionStorage.setItem("usuarioCorrente", JSON.stringify(usuarioCorrente));
        console.log('Usuário logado, dados salvos no sessionStorage:', sessionStorage.getItem('usuarioCorrente'));
        return { success: true, message: "Login realizado com sucesso!" };
      } else {
        return { success: false, message: "Senha incorreta.", field: "senha" };
      }
    }
  }
  return { success: false, message: "Usuário não encontrado.", field: "login" };
}

function logoutUser() {
  sessionStorage.removeItem("usuarioCorrente");
  window.location = LOGIN_URL;
}

function getNextUserId() {
  if (!db_usuarios || db_usuarios.length === 0) {
    return 1;
  }
  const maxId = Math.max(...db_usuarios.map((usuario) => usuario.id || 0));
  return maxId + 1;
}

async function addUser(nome, login, senha, email, senha2) {
  const nomeValidation = validateNome(nome);
  if (!nomeValidation.valid) {
    return { success: false, message: nomeValidation.message, field: nomeValidation.field };
  }

  const loginValidation = validateLogin(login);
  if (!loginValidation.valid) {
    return { success: false, message: loginValidation.message, field: loginValidation.field };
  }

  const emailValidation = validateEmail(email);
  if (!emailValidation.valid) {
    return { success: false, message: emailValidation.message, field: emailValidation.field };
  }

  const passwordValidation = validatePassword(senha);
  if (!passwordValidation.valid) {
    return { success: false, message: passwordValidation.message, field: passwordValidation.field };
  }

  if (senha !== senha2) {
    return { success: false, message: "As senhas informadas não conferem.", field: "senha2" };
  }

  for (var i = 0; i < db_usuarios.length; i++) {
    if (db_usuarios[i].login === login) {
      return { success: false, message: "Este login já está em uso. Por favor, escolha outro.", field: "login" };
    }
    if (db_usuarios[i].email === email) {
      return { success: false, message: "Este email já está em uso. Por favor, escolha outro.", field: "email" };
    }
  }

  const novoId = getNextUserId();
  const usuario = {
    id: novoId,
    login: login,
    senha: senha,
    nome: nome,
    email: email,
    foto: "",
    telefone: "",
    data_nasc: "",
    localizacao: "",
    profissao: "",
    formacao: "",
    empresa: "",
    habilidades: "",
    idioma: "pt_BR",
    notificacoes: {
      email: false,
      relatorio: false,
      metas: false,
    },
    infos: [
      {
        atividades: [],
        feedback: [],
        recomendacoes: []
      },
    ],
  };

  try {
    const response = await fetch(API_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(usuario),
    });

    if (!response.ok) {
      throw new Error("Erro ao inserir usuário");
    }

    const data = await response.json();
    db_usuarios.push(data);
    return { success: true, message: "Usuário registrado com sucesso!" };
  } catch (error) {
    console.error("Erro ao inserir usuário via API JSONServer:", error);
    return { success: false, message: "Erro ao inserir usuário. Tente novamente.", field: "geral" };
  }
}

function showUserInfo(element) {
  const elemUser = document.getElementById(element);
  if (elemUser) {
    elemUser.innerHTML = `${usuarioCorrente.nome} (${usuarioCorrente.login}) 
                    <a onclick="logoutUser()">❌</a>`;
  }
}

function displayMessage(message, type = 'error') {
  const container = document.getElementById('notification-container');
  const notification = document.createElement('div');
  notification.className = `notification ${type} show`;
  notification.textContent = message;

  container.appendChild(notification);

  setTimeout(() => {
    notification.classList.remove('show');
    setTimeout(() => notification.remove(), 300);
  }, 3000);
}

initLoginApp();