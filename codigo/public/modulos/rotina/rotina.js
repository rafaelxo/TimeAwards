document.addEventListener('DOMContentLoaded', () => {
  // Verifica se o usuário está logado
  const usuarioCorrenteJSON = sessionStorage.getItem('usuarioCorrente');
  if (!usuarioCorrenteJSON) {
    window.location.href = '../login/login.html';
    return;
  }

  const usuarioCorrente = JSON.parse(usuarioCorrenteJSON);
  const nomeUsuario = usuarioCorrente.nome || 'usuário';
  const saudacao = document.getElementById('texto-subtitulo');
  if (saudacao) {
    saudacao.textContent = `${nomeUsuario}, gerencie suas atividades diárias`;
  }

  // Define as metas de horas por classe
  const metasPorClasse = {
    "Atividade Física": 2,
    "Trabalho": 6,
    "Tela": 2,
    "Estudos": 4,
    "Sono": 8,
    "Lazer": 2
  };

  // Elementos do formulário
  const selectClasse = document.querySelector('select.input-formulario');
  const formulario = document.getElementById('formulario');
  const botaoCancelar = document.querySelector('.botao-cancelar');
  const botaoLimpar = document.querySelector('.botao-limpar');
  const botaoAdicionar = document.querySelector('.botao-enviar');
  const inputBusca = document.querySelector('.input-busca');
  const botaoBusca = document.querySelector('.botao-busca');

  // Função para limpar o formulário
  function limparFormulario() {
    formulario.reset();
  }

  // Função para coletar os dados do formulário
  function coletarDadosFormulario() {
    const nomeAtividade = document.querySelector('input[placeholder="Digite o nome"]').value.trim();
    const classe = selectClasse.value;
    const horasGastas = parseFloat(document.querySelector('input[placeholder="Horas gastas"]').value) || 0;
    const metaHoras = metasPorClasse[classe] || 0;
    const prioridade = document.querySelector('#prioridade select.input-formulario').value;
    const dataHora = new Date().toISOString();

    return {
      nomeAtividade,
      classe,
      horasGastas,
      metaHoras,
      metaCumprida: horasGastas >= metaHoras,
      prioridade,
      dataHora
    };
  }

  // Função para salvar os dados no JSON
  async function salvarAtividade(dados) {
    try {
      botaoAdicionar.disabled = true;
      const responseUsuario = await fetch(`http://localhost:3000/usuarios/${usuarioCorrente.id}`);
      if (!responseUsuario.ok) throw new Error('Erro ao carregar dados do usuário.');
      const usuario = await responseUsuario.json();

      const atividadesAtuais = usuario.infos[0].atividades || [];
      atividadesAtuais.push(dados);

      const response = await fetch(`http://localhost:3000/usuarios/${usuarioCorrente.id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          infos: [{ ...usuario.infos[0], atividades: atividadesAtuais }]
        })
      });

      if (!response.ok) throw new Error('Erro ao salvar atividade.');
      alert('Atividade adicionada com sucesso!');
      limparFormulario();
    } catch (error) {
      console.error('Erro ao salvar atividade:', error);
      alert(`Erro ao salvar atividade: ${error.message}. Tente novamente.`);
    } finally {
      botaoAdicionar.disabled = false;
    }
  }

  // Evento do botão Cancelar
  botaoCancelar.addEventListener('click', () => {
    window.location.href = '../../index.html';
  });

  // Evento do botão Limpar
  botaoLimpar.addEventListener('click', limparFormulario);

  // Evento do botão Adicionar Atividade
  botaoAdicionar.addEventListener('click', () => {
    const dados = coletarDadosFormulario();

    // Validações
    if (!dados.nomeAtividade || !dados.classe || !dados.horasGastas) {
      alert('Por favor, preencha os campos obrigatórios: Nome, Classe e Horas.');
      return;
    }

    if (dados.horasGastas <= 0) {
      alert('As horas gastas devem ser maiores que zero.');
      return;
    }

    if (dados.metaHoras <= 0) {
      alert('A meta de horas deve ser maior que zero.');
      return;
    }

    salvarAtividade(dados);
  });

  // Evento para o campo de busca e botão de busca
  if (inputBusca) {
    inputBusca.addEventListener('input', () => {
      alert('Busca não exibe resultados na Rotina. As atividades são exibidas no Monitoramento.');
    });
  }

  if (botaoBusca) {
    botaoBusca.addEventListener('click', () => {
      alert('Busca não exibe resultados na Rotina. As atividades são exibidas no Monitoramento.');
    });
  }
});