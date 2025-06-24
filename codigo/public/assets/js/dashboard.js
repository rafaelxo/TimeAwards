document.addEventListener('DOMContentLoaded', async () => {
  // 1) Verifica se o usuário está logado
  const userJSON = sessionStorage.getItem('usuarioCorrente');
  if (!userJSON) {
    window.location.href = '../modulos/login/login.html';
    return;
  }
  const user = JSON.parse(userJSON);
  const textoSubtitulo = document.getElementById('texto-subtitulo');
  if (textoSubtitulo) {
    textoSubtitulo.textContent = `${user.nome || 'usuário'}, acompanhe sua rotina em gráficos`;
  }

  // 2) Definição das categorias e metas fixas
  const categorias = ['Atividade Física', 'Trabalho', 'Estudos', 'Tela', 'Sono', 'Lazer'];
  const metasFixas = {
    'Atividade Física': 2,
    Trabalho: 6,
    Estudos: 4,
    Tela: 2,
    Sono: 8,
    Lazer: 2,
  };
  const mapa = {
    'Atividade Física': 'Atividade Física',
    Trabalho: 'Trabalho',
    Estudos: 'Estudos',
    Tela: 'Tela',
    Sono: 'Sono',
    Lazer: 'Lazer',
  };

  // 3) Formatação
  const fmt = (v) => (typeof v === 'number' && !isNaN(v) ? v.toFixed(2) : '0.00');

  // 4) Função auxiliar para obter o início do dia
  const getInicioDia = (data) => {
    return new Date(data.getFullYear(), data.getMonth(), data.getDate());
  };

  // 5) Cálculo de KPIs
  function calcKPIs(arr) {
    const total = arr.reduce((s, a) => s + (parseFloat(a.horasGastas) || 0), 0);
    const metas = arr.filter((a) => (parseFloat(a.horasGastas) || 0) >= (a.metaHoras || 0)).length;
    const alertas = arr.filter(
      (a) =>
        a.prioridade === 'Alta' || (parseFloat(a.horasGastas) || 0) > (a.metaHoras || 0) * 1.5
    ).length;
    return { tempoTotal: fmt(total), metas: `${metas}/${arr.length}`, alertas };
  }

  // 6) Distribuição para o gráfico de rosca
  function calcDistrib(arr) {
    const tot = arr.reduce((s, a) => s + (parseFloat(a.horasGastas) || 0), 0) || 1;
    const cat = categorias.reduce((o, c) => ({ ...o, [c]: 0 }), {});
    arr.forEach((a) => {
      const c = mapa[a.classe] || 'Tela';
      if (cat[c] !== undefined) cat[c] += parseFloat(a.horasGastas) || 0;
    });
    return categorias.map((c) => (cat[c] / tot) * 100);
  }

  // 7) Progresso por classe (baseado no dia atual)
  function calcProgPorClasse(arr, classe) {
    const hoje = getInicioDia(new Date());
    const atividadesHoje = arr.filter(
      (a) => getInicioDia(new Date(a.dataHora)).toDateString() === hoje.toDateString() && mapa[a.classe] === classe
    );
    console.log(`Atividades ${classe}:`, atividadesHoje); // Depuração
    const feitas = atividadesHoje.reduce((s, a) => s + (parseFloat(a.horasGastas) || 0), 0);
    const meta = metasFixas[classe] || 0;
    return { feitas, meta, metaCumprida: feitas >= meta && meta > 0 };
  }

  // 8) Horas das últimas 4 semanas
  function calcSemanas(arr) {
    const hoje = new Date();
    const corte = new Date(hoje.getTime() - 28 * 24 * 60 * 60 * 1000);
    corte.setHours(0, 0, 0, 0);
    const grupos = [0, 0, 0, 0];
    arr.filter((a) => new Date(a.dataHora) >= corte).forEach((a) => {
      const diff = Math.floor((hoje - new Date(a.dataHora)) / (1000 * 60 * 60 * 24));
      const idx = Math.min(3, Math.floor(diff / 7));
      grupos[idx] += parseFloat(a.horasGastas) || 0;
    });
    return grupos.reverse().map((h, i) => ({ label: `Semana ${i + 1}`, horas: h }));
  }

  // 9) Exibir interface
  function exibir(arr) {
    // KPIs
    const k = calcKPIs(arr);
    const kpiTempoTotal = document.getElementById('kpi-tempo-total');
    const kpiMetasAtingidas = document.getElementById('kpi-metas-atingidas');
    const kpiAlertas = document.getElementById('kpi-alertas');
    const progressoTempoTotal = document.getElementById('progresso-tempo-total');
    const progressoMetasAtingidas = document.getElementById('progresso-metas-atingidas');
    const progressoAlertas = document.getElementById('progresso-alertas');

    if (kpiTempoTotal) kpiTempoTotal.textContent = `${k.tempoTotal} horas`;
    if (kpiMetasAtingidas) kpiMetasAtingidas.textContent = k.metas;
    if (kpiAlertas) kpiAlertas.textContent = `${k.alertas} alertas`;

    // Progresso para KPIs
    if (progressoTempoTotal) {
      progressoTempoTotal.style.width = `${Math.min((k.tempoTotal / 24) * 100, 100)}%`; // 24 horas = 1 dia
    }
    if (progressoMetasAtingidas) {
      const [atingidas, total] = k.metas.split('/').map(Number);
      progressoMetasAtingidas.style.width = total > 0 ? (atingidas / total) * 100 + '%' : '0%';
      progressoMetasAtingidas.classList.toggle('progresso-completo', atingidas >= total);
    }
    if (progressoAlertas) {
      progressoAlertas.style.width = k.alertas > 0 ? Math.min((k.alertas / 10) * 100, 100) + '%' : '0%';
      progressoAlertas.classList.toggle('progresso-completo', k.alertas === 0);
    }

    // Gráfico de rosca (distribuição de tempo)
    const canvasRosca = document.getElementById('canvas-rosca');
    if (canvasRosca) {
      new Chart(canvasRosca.getContext('2d'), {
        type: 'doughnut',
        data: {
          labels: categorias,
          datasets: [
            {
              data: calcDistrib(arr),
              backgroundColor: ['#f59e0b', '#6366f1', '#10b981', '#a855f7', '#0ea5e9', '#f97316'],
            },
          ],
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: { position: 'top' },
            tooltip: {
              callbacks: {
                label: (context) => `${context.label}: ${fmt(context.raw)}%`,
              },
            },
          },
        },
      });
    }

    // Barras de progresso por categoria
    categorias.forEach((classe) => {
      const slug = classe
        .toLowerCase()
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/\s+/g, '-');
      const bar = document.getElementById(`progresso-${slug}`);
      if (!bar) {
        console.log(`Barra ${slug} não encontrada`); // Depuração
        return;
      }

      const { feitas, meta, metaCumprida } = calcProgPorClasse(arr, classe);
      const pct = meta > 0 ? Math.min((feitas / meta) * 100, 100) : 0;
      bar.style.width = `${pct}%`;
      bar.setAttribute('title', `${fmt(feitas)}h de ${fmt(meta)}h (${pct.toFixed(0)}%)`);
      bar.classList.toggle('progresso-completo', metaCumprida);
    });

    // Gráfico de linha (últimas 4 semanas)
    const canvasLinha = document.getElementById('canvas-linha');
    if (canvasLinha) {
      const semanas = calcSemanas(arr);
      new Chart(canvasLinha.getContext('2d'), {
        type: 'line',
        data: {
          labels: semanas.map((x) => x.label),
          datasets: [
            {
              label: 'Horas',
              data: semanas.map((x) => x.horas),
              fill: false,
              tension: 0.4,
              borderColor: '#0ea5e9',
            },
          ],
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          scales: {
            y: { beginAtZero: true, title: { display: true, text: 'Horas' } },
            x: { title: { display: true, text: 'Semanas' } },
          },
        },
      });
    }
  }

  // 10) Carregar dados
  async function carregar() {
    try {
      const loadingIndicator = document.getElementById('loading-indicator');
      if (loadingIndicator) loadingIndicator.style.display = 'block';

      const res = await fetch(`http://localhost:3000/usuarios/${user.id}`);
      if (!res.ok) throw new Error('Erro ao carregar dados do usuário.');
      const js = await res.json();
      console.log('Dados da API:', js); // Depuração
      const atividades = js?.infos?.[0]?.atividades || [];
      exibir(atividades);
    } catch (e) {
      console.error('Erro ao carregar atividades:', e);
      alert('Falha ao carregar atividades. Tente novamente.');
    } finally {
      const loadingIndicator = document.getElementById('loading-indicator');
      if (loadingIndicator) loadingIndicator.style.display = 'none';
    }
  }

  // 11) Inicialização
  await carregar();
});