document.addEventListener('DOMContentLoaded', () => {
  // Verifica se o usuário está logado
  const usuarioCorrenteJSON = sessionStorage.getItem('usuarioCorrente');
  if (!usuarioCorrenteJSON) {
    window.location.href = '../login/login.html';
    return;
  }

  const usuarioCorrente = JSON.parse(usuarioCorrenteJSON);
  const textoSubtitulo = document.getElementById('texto-subtitulo');
  if (textoSubtitulo) {
    textoSubtitulo.textContent = `${usuarioCorrente.nome || 'usuário'}, acompanhe sua rotina com clareza`;
  }

  // Elementos do formulário
  const tempoUsoHojeInput = document.getElementById('tempo-uso-hoje');
  const mediaHorasDiariasInput = document.getElementById('media-horas-diarias');
  const horasTotaisInput = document.getElementById('horas-totais');
  const mediaTrabalhoInput = document.getElementById('media-trabalho');
  const mediaEstudosInput = document.getElementById('media-estudos');
  const mediaAtividadeFisicaInput = document.getElementById('media-atividade-fisica');
  const mediaTelaInput = document.getElementById('media-tela');
  const mediaSonoInput = document.getElementById('media-sono');
  const mediaLazerInput = document.getElementById('media-lazer');
  const metaTrabalhoP = document.getElementById('meta-trabalho');
  const metaEstudoP = document.getElementById('meta-estudo');
  const metaAtividadeFisicaP = document.getElementById('meta-atividade-fisica');
  const metaTelaP = document.getElementById('meta-tela');
  const metaSonoP = document.getElementById('meta-sono');
  const metaLazerP = document.getElementById('meta-lazer');
  const progressoTrabalho = document.getElementById('progresso-trabalho');
  const progressoEstudo = document.getElementById('progresso-estudo');
  const progressoAtividadeFisica = document.getElementById('progresso-atividade-fisica');
  const progressoTela = document.getElementById('progresso-tela');
  const progressoSono = document.getElementById('progresso-sono');
  const progressoLazer = document.getElementById('progresso-lazer');
  const botaoAtualizar = document.getElementById('atualizar');
  const botaoCancelar = document.getElementById('cancelar');
  const botaoLimpar = document.getElementById('limpar');
  const tabelaAtividades = document.getElementById('tabela-atividades');
  const loadingIndicator = document.getElementById('loading-indicator');

  let atividades = [];
  let ordemAtual = { coluna: null, ascendente: true };

  const mapeamentoCategorias = {
    Trabalho: 'Trabalho',
    Estudos: 'Estudos',
    'Atividade Física': 'Atividade Física',
    Tela: 'Tela',
    Sono: 'Sono',
    Lazer: 'Lazer',
  };

  // Metas diárias fixas por categoria (em horas), sincronizadas com o código de cadastro
  const metasFixas = {
    Trabalho: 6,
    Estudos: 4,
    'Atividade Física': 2,
    Tela: 2,
    Sono: 8,
    Lazer: 2,
  };

  // Funções auxiliares
  const formatarNumero = (valor) => {
    const num = parseFloat(valor);
    return !isNaN(num) ? num.toFixed(2) : '0.00';
  };

  const getInicioDia = (data) => {
    return new Date(data.getFullYear(), data.getMonth(), data.getDate());
  };

  // Função para carregar dados
  async function carregarDados(botao = null) {
    try {
      if (botao) botao.disabled = true;
      if (loadingIndicator) loadingIndicator.style.display = 'block';
      const response = await fetch(`http://localhost:3000/usuarios/${usuarioCorrente.id}`);
      if (!response.ok) {
        throw new Error('Erro ao carregar dados.');
      }
      const usuario = await response.json();
      atividades = usuario.infos[0].atividades || [];
      atualizarInterface();
    } catch (error) {
      console.error('Erro ao carregar dados:', error);
      alert('Erro ao carregar dados. Tente novamente!');
    } finally {
      if (botao) botao.disabled = false;
      if (loadingIndicator) loadingIndicator.style.display = 'none';
    }
  }

  // Funções de cálculo
  const calcularHorasTotais = () =>
    atividades.reduce(
      (total, atividade) => total + (parseFloat(atividade.horasGastas) || 0),
      0,
    );

  const calcularTempoUsoHoje = () => {
    const hoje = getInicioDia(new Date());
    const atividadesHoje = atividades.filter(
      (atividade) =>
        getInicioDia(new Date(atividade.dataHora)).toDateString() === hoje.toDateString(),
    );
    return atividadesHoje.reduce(
      (total, atividade) => total + (parseFloat(atividade.horasGastas) || 0),
      0,
    );
  };

  const calcularMediaHorasDiarias = () => {
    const diasUnicos = new Set(
      atividades.map((atividade) => getInicioDia(new Date(atividade.dataHora)).toDateString()),
    ).size;
    return diasUnicos > 0 ? calcularHorasTotais() / diasUnicos : 0;
  };

  const calcularMediaDiaria = () => {
    const diasComAtividades = new Set(
      atividades.map((atividade) => getInicioDia(new Date(atividade.dataHora)).toDateString()),
    );
    const diasUnicos = diasComAtividades.size;
    if (diasUnicos === 0) {
      return {
        Trabalho: 0,
        Estudos: 0,
        'Atividade Física': 0,
        Tela: 0,
        Sono: 0,
        Lazer: 0,
      };
    }

    const horasPorCategoriaEDia = atividades.reduce((acc, atividade) => {
      const categoria = mapeamentoCategorias[atividade.classe] || 'Tela';
      const dia = getInicioDia(new Date(atividade.dataHora)).toDateString();
      if (!acc[dia]) {
        acc[dia] = {
          Trabalho: 0,
          Estudos: 0,
          'Atividade Física': 0,
          Tela: 0,
          Sono: 0,
          Lazer: 0,
        };
      }
      acc[dia][categoria] += parseFloat(atividade.horasGastas) || 0;
      return acc;
    }, {});

    const horasTotaisPorCategoria = Object.values(horasPorCategoriaEDia).reduce(
      (acc, dia) => {
        acc.Trabalho += dia.Trabalho;
        acc.Estudos += dia.Estudos;
        acc['Atividade Física'] += dia['Atividade Física'];
        acc.Tela += dia.Tela;
        acc.Sono += dia.Sono;
        acc.Lazer += dia.Lazer;
        return acc;
      },
      {
        Trabalho: 0,
        Estudos: 0,
        'Atividade Física': 0,
        Tela: 0,
        Sono: 0,
        Lazer: 0,
      },
    );

    return {
      Trabalho: horasTotaisPorCategoria.Trabalho / diasUnicos,
      Estudos: horasTotaisPorCategoria.Estudos / diasUnicos,
      'Atividade Física': horasTotaisPorCategoria['Atividade Física'] / diasUnicos,
      Tela: horasTotaisPorCategoria.Tela / diasUnicos,
      Sono: horasTotaisPorCategoria.Sono / diasUnicos,
      Lazer: horasTotaisPorCategoria.Lazer / diasUnicos,
    };
  };

  const calcularLimitesDiarios = () => {
    const hoje = getInicioDia(new Date());
    const atividadesHoje = atividades.filter(
      (atividade) =>
        getInicioDia(new Date(atividade.dataHora)).toDateString() === hoje.toDateString(),
    );

    const limites = {
      Trabalho: { meta: metasFixas.Trabalho, realizado: 0, metaCumprida: false },
      Estudos: { meta: metasFixas.Estudos, realizado: 0, metaCumprida: false },
      'Atividade Física': {
        meta: metasFixas['Atividade Física'],
        realizado: 0,
        metaCumprida: false,
      },
      Tela: { meta: metasFixas.Tela, realizado: 0, metaCumprida: false },
      Sono: { meta: metasFixas.Sono, realizado: 0, metaCumprida: false },
      Lazer: { meta: metasFixas.Lazer, realizado: 0, metaCumprida: false },
    };

    if (atividadesHoje.length > 0) {
      atividadesHoje.forEach((atividade) => {
        const categoria = mapeamentoCategorias[atividade.classe] || 'Tela';
        limites[categoria].realizado += parseFloat(atividade.horasGastas) || 0;
      });
    }

    Object.keys(limites).forEach((categoria) => {
      limites[categoria].metaCumprida =
        limites[categoria].realizado >= limites[categoria].meta &&
        limites[categoria].meta > 0;
    });

    return limites;
  };

  // Função para atualizar a tabela de atividades
  const atualizarTabela = () => {
    if (!tabelaAtividades) return;
    const tbody = tabelaAtividades.querySelector('tbody');
    if (!tbody) return;
    tbody.innerHTML = '';
    if (atividades.length === 0) {
      tbody.innerHTML =
        '<tr><td colspan="5">Nenhuma atividade encontrada.</td></tr>';
      return;
    }
    atividades.forEach((atividade) => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td>${atividade.nomeAtividade || '-'}</td>
        <td>${atividade.classe || '-'}</td>
        <td>${formatarNumero(atividade.horasGastas)}</td>
        <td>${atividade.prioridade || '-'}</td>
        <td>${new Date(atividade.dataHora).toLocaleDateString('pt-BR', { timeZone: 'UTC' })}</td>
      `;
      tbody.appendChild(tr);
    });
  };

  // Função para atualizar a interface
  const atualizarInterface = () => {
    const tempoUsoHoje = calcularTempoUsoHoje();
    const mediaHorasDiarias = calcularMediaHorasDiarias();
    const horasTotais = calcularHorasTotais();
    const medias = calcularMediaDiaria();
    const limites = calcularLimitesDiarios();

    // Atualizar campos de resumo de horas
    if (tempoUsoHojeInput)
      tempoUsoHojeInput.value = `${formatarNumero(tempoUsoHoje)} horas`;
    if (mediaHorasDiariasInput)
      mediaHorasDiariasInput.value = `${formatarNumero(mediaHorasDiarias)} horas/dia`;
    if (horasTotaisInput)
      horasTotaisInput.value = `${formatarNumero(horasTotais)} horas`;

    // Atualizar médias diárias por categoria
    if (mediaTrabalhoInput)
      mediaTrabalhoInput.value = `${formatarNumero(medias.Trabalho)} horas/dia`;
    if (mediaEstudosInput)
      mediaEstudosInput.value = `${formatarNumero(medias.Estudos)} horas/dia`;
    if (mediaAtividadeFisicaInput)
      mediaAtividadeFisicaInput.value = `${formatarNumero(medias['Atividade Física'])} horas/dia`;
    if (mediaTelaInput)
      mediaTelaInput.value = `${formatarNumero(medias.Tela)} horas/dia`;
    if (mediaSonoInput)
      mediaSonoInput.value = `${formatarNumero(medias.Sono)} horas/dia`;
    if (mediaLazerInput)
      mediaLazerInput.value = `${formatarNumero(medias.Lazer)} horas/dia`;

    // Atualizar metas e limites diários com preview
    if (metaTrabalhoP) {
      metaTrabalhoP.textContent = `Meta: ${formatarNumero(limites.Trabalho.meta)}h | Realizado: ${formatarNumero(limites.Trabalho.realizado)}h${limites.Trabalho.metaCumprida ? ' (Meta atingida!)' : ''}`;
      metaTrabalhoP.classList.toggle('meta-atingida', limites.Trabalho.metaCumprida);
    }
    if (metaEstudoP) {
      metaEstudoP.textContent = `Meta: ${formatarNumero(limites.Estudos.meta)}h | Realizado: ${formatarNumero(limites.Estudos.realizado)}h${limites.Estudos.metaCumprida ? ' (Meta atingida!)' : ''}`;
      metaEstudoP.classList.toggle('meta-atingida', limites.Estudos.metaCumprida);
    }
    if (metaAtividadeFisicaP) {
      metaAtividadeFisicaP.textContent = `Meta: ${formatarNumero(limites['Atividade Física'].meta)}h | Realizado: ${formatarNumero(limites['Atividade Física'].realizado)}h${limites['Atividade Física'].metaCumprida ? ' (Meta atingida!)' : ''}`;
      metaAtividadeFisicaP.classList.toggle('meta-atingida', limites['Atividade Física'].metaCumprida);
    }
    if (metaTelaP) {
      metaTelaP.textContent = `Meta: ${formatarNumero(limites.Tela.meta)}h | Realizado: ${formatarNumero(limites.Tela.realizado)}h${limites.Tela.metaCumprida ? ' (Meta atingida!)' : ''}`;
      metaTelaP.classList.toggle('meta-atingida', limites.Tela.metaCumprida);
    }
    if (metaSonoP) {
      metaSonoP.textContent = `Meta: ${formatarNumero(limites.Sono.meta)}h | Realizado: ${formatarNumero(limites.Sono.realizado)}h${limites.Sono.metaCumprida ? ' (Meta atingida!)' : ''}`;
      metaSonoP.classList.toggle('meta-atingida', limites.Sono.metaCumprida);
    }
    if (metaLazerP) {
      metaLazerP.textContent = `Meta: ${formatarNumero(limites.Lazer.meta)}h | Realizado: ${formatarNumero(limites.Lazer.realizado)}h${limites.Lazer.metaCumprida ? ' (Meta atingida!)' : ''}`;
      metaLazerP.classList.toggle('meta-atingida', limites.Lazer.metaCumprida);
    }

    // Atualizar barras de progresso
    if (progressoTrabalho) {
      const progresso =
        limites.Trabalho.meta > 0
          ? Math.min((limites.Trabalho.realizado / limites.Trabalho.meta) * 100, 100)
          : 0;
      progressoTrabalho.style.width = `${progresso.toFixed(2)}%`;
      progressoTrabalho.classList.toggle('progresso-completo', limites.Trabalho.metaCumprida);
    }
    if (progressoEstudo) {
      const progresso =
        limites.Estudos.meta > 0
          ? Math.min((limites.Estudos.realizado / limites.Estudos.meta) * 100, 100)
          : 0;
      progressoEstudo.style.width = `${progresso.toFixed(2)}%`;
      progressoEstudo.classList.toggle('progresso-completo', limites.Estudos.metaCumprida);
    }
    if (progressoAtividadeFisica) {
      const progresso =
        limites['Atividade Física'].meta > 0
          ? Math.min(
              (limites['Atividade Física'].realizado / limites['Atividade Física'].meta) * 100,
              100,
            )
          : 0;
      progressoAtividadeFisica.style.width = `${progresso.toFixed(2)}%`;
      progressoAtividadeFisica.classList.toggle('progresso-completo', limites['Atividade Física'].metaCumprida);
    }
    if (progressoTela) {
      const progresso =
        limites.Tela.meta > 0
          ? Math.min((limites.Tela.realizado / limites.Tela.meta) * 100, 100)
          : 0;
      progressoTela.style.width = `${progresso.toFixed(2)}%`;
      progressoTela.classList.toggle('progresso-completo', limites.Tela.metaCumprida);
    }
    if (progressoSono) {
      const progresso =
        limites.Sono.meta > 0
          ? Math.min((limites.Sono.realizado / limites.Sono.meta) * 100, 100)
          : 0;
      progressoSono.style.width = `${progresso.toFixed(2)}%`;
      progressoSono.classList.toggle('progresso-completo', limites.Sono.metaCumprida);
    }
    if (progressoLazer) {
      const progresso =
        limites.Lazer.meta > 0
          ? Math.min((limites.Lazer.realizado / limites.Lazer.meta) * 100, 100)
          : 0;
      progressoLazer.style.width = `${progresso.toFixed(2)}%`;
      progressoLazer.classList.toggle('progresso-completo', limites.Lazer.metaCumprida);
    }

    atualizarTabela();
  };

  // Função para ordenar atividades
  const ordenarAtividades = (coluna) => {
    if (ordemAtual.coluna === coluna) {
      ordemAtual.ascendente = !ordemAtual.ascendente;
    } else {
      ordemAtual.coluna = coluna;
      ordemAtual.ascendente = true;
    }

    atividades.sort((a, b) => {
      let valorA = a[coluna];
      let valorB = b[coluna];

      if (coluna === 'horasGastas') {
        valorA = parseFloat(valorA) || 0;
        valorB = parseFloat(valorB) || 0;
      } else if (coluna === 'dataHora') {
        valorA = new Date(valorA).getTime();
        valorB = new Date(valorB).getTime();
      } else {
        valorA = (valorA || '').toString().toLowerCase();
        valorB = (valorB || '').toString().toLowerCase();
      }

      return ordemAtual.ascendente
        ? valorA - valorB || valorA.localeCompare(valorB)
        : valorB - valorA || valorB.localeCompare(valorA);
    });

    document.querySelectorAll('.sortable').forEach((th) => {
      th.classList.remove('asc', 'desc');
      if (th.getAttribute('data-coluna') === coluna) {
        th.classList.add(ordemAtual.ascendente ? 'asc' : 'desc');
      }
    });

    atualizarInterface();
  };

  // Eventos
  if (botaoAtualizar) {
    botaoAtualizar.addEventListener('click', (e) => {
      e.preventDefault();
      carregarDados(botaoAtualizar);
    });
  }
  if (botaoCancelar) {
    botaoCancelar.addEventListener('click', () => {
      window.location.href = '../../index.html';
    });
  }
  if (botaoLimpar) {
    botaoLimpar.addEventListener('click', () => {
      atualizarInterface();
    });
  }
  document.querySelectorAll('.sortable').forEach((th) => {
    th.addEventListener('click', () =>
      ordenarAtividades(th.getAttribute('data-coluna')),
    );
  });

  // Carrega atividades ao iniciar
  carregarDados();
});