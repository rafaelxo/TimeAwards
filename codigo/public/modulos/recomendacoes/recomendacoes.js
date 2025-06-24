document.addEventListener("DOMContentLoaded", async () => {
  const usuarioCorrenteJSON = sessionStorage.getItem("usuarioCorrente");
  if (!usuarioCorrenteJSON) {
    window.location.href = "../login/login.html";
    return;
  }

  const usuario = JSON.parse(usuarioCorrenteJSON);
  document.getElementById("texto-subtitulo").textContent = `${usuario.nome}, trouxemos para você recomendações personalizadas`;

  const container = document.querySelector(".grade-formulario");

  const atualizarIndicadores = (atividades) => {
    const totalHoras = atividades.reduce((s, a) => s + (a.horasGastas || 0), 0);
    const alertas = atividades.filter(a => !a.metaCumprida && a.horasGastas > 0).length;
    const metas = atividades.filter(a => a.metaCumprida).length;
    const total = atividades.length || 1;

    document.getElementById("kpi-tempo").textContent = `${totalHoras.toFixed(1)} horas`;
    document.getElementById("kpi-alerta").textContent = `${alertas} alerta(s)`;
    document.getElementById("kpi-metas").textContent = `${metas}/${total} metas`;

    document.getElementById("progresso-tempo").style.width = Math.min((totalHoras / 24) * 100, 100) + "%";
    document.getElementById("progresso-alerta").style.width = Math.min(alertas * 20, 100) + "%";
    document.getElementById("progresso-metas").style.width = Math.min((metas / total) * 100, 100) + "%";
  };

  const gerarRecomendacoesIA = (atividades) => {
    const porClasse = (cls) => atividades.find(a => a.classe === cls);
    return [
      porClasse("Atividade Física")?.metaCumprida === false
        ? { titulo: "Mexa-se mais!", descricao: "Você ainda não atingiu a meta de exercícios hoje.", relevancia: "Moderado", status: false }
        : { titulo: "Atividade física em dia", descricao: "Continue se movimentando!", relevancia: "Leve", status: false },

      porClasse("Estudos")?.metaCumprida === false
        ? { titulo: "Hora de estudar", descricao: "Você estudou pouco hoje. Que tal 1h de revisão?", relevancia: "Alto", status: false }
        : { titulo: "Bom ritmo de estudo", descricao: "Seu foco está excelente!", relevancia: "Leve", status: false },

      porClasse("Eletrônicos")?.horasGastas > porClasse("Eletrônicos")?.metaHoras
        ? { titulo: "Desconecte um pouco", descricao: "Reduza o tempo com telas. Faça uma pausa!", relevancia: "Alto", status: false }
        : { titulo: "Uso consciente de telas", descricao: "Seu tempo de tela está controlado.", relevancia: "Leve", status: false },

      porClasse("Trabalho")?.horasGastas > porClasse("Trabalho")?.metaHoras
        ? { titulo: "Pausa merecida", descricao: "Você trabalhou bastante. Respire e relaxe.", relevancia: "Moderado", status: false }
        : { titulo: "Rotina equilibrada", descricao: "Ótimo gerenciamento do tempo de trabalho!", relevancia: "Leve", status: false },

      porClasse("Sono")?.horasGastas < porClasse("Sono")?.metaHoras
        ? { titulo: "Priorize o descanso", descricao: "Você dormiu menos do que o recomendado. Tente descansar mais.", relevancia: "Alto", status: false }
        : { titulo: "Sono em dia", descricao: "Seu descanso está no caminho certo!", relevancia: "Leve", status: false },

      porClasse("Lazer")?.metaCumprida === false
        ? { titulo: "Reserve um tempo para o lazer!", descrição: "Que tal um hobby ou tempo com amigos?", relevancia: "Moderado", status: false }
        : { titulo: "Lazer equilibrado", descrição: "Você está aproveitando bem seus momentos de lazer!", relevancia: "Leve", status: false }
    ];
  };

  const atualizarRecomendacoes = async (userId, novas) => {
    const res = await fetch(`http://localhost:3000/usuarios/${userId}`);
    const user = await res.json();
    const infos = user.infos.map((info, i) => i === 0 ? { ...info, recomendacoes: novas } : info);
    await fetch(`http://localhost:3000/usuarios/${userId}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ infos })
    });
  };

  const carregarRecomendacoes = async () => {
    const res = await fetch(`http://localhost:3000/usuarios/${usuario.id}`);
    const user = await res.json();
    const atividades = user.infos[0].atividades || [];
    const recomendacoes = user.infos[0].recomendacoes || [];

    atualizarIndicadores(atividades);
    container.innerHTML = "";

    if (!recomendacoes.length) {
      container.innerHTML = "<p>Nenhuma recomendação disponível. Clique para gerar novas.</p>";
      return;
    }

    recomendacoes.forEach((rec, i) => {
      const div = document.createElement("div");
      div.className = "recomendacao-card " + rec.relevancia.toLowerCase();
      div.innerHTML = `
        <p class="card-text"><strong>Título:</strong> ${rec.titulo}</p>
        <p class="card-text"><strong>Descrição:</strong> ${rec.descricao}</p>
        <p class="card-text"><strong>Relevância:</strong> ${rec.relevancia}</p>
        <button class="btn-concluir" data-index="${i}" ${rec.status ? "disabled" : ""}>
          ${rec.status ? "Concluído" : "Marcar como concluído"}
        </button>
      `;
      container.appendChild(div);
    });

    document.querySelectorAll(".btn-concluir").forEach(btn => {
      btn.addEventListener("click", async e => {
        const index = parseInt(e.target.dataset.index);
        recomendacoes[index].status = true;
        await atualizarRecomendacoes(usuario.id, recomendacoes);
        await carregarRecomendacoes();
      });
    });
  };

  document.querySelector(".botao-enviar").addEventListener("click", async () => {
    const res = await fetch(`http://localhost:3000/usuarios/${usuario.id}`);
    const user = await res.json();
    const atividades = user.infos[0].atividades || [];
    const novas = gerarRecomendacoesIA(atividades);
    await atualizarRecomendacoes(usuario.id, novas);
    await carregarRecomendacoes();
  });

  document.querySelector(".botao-limpar").addEventListener("click", async () => {
    await atualizarRecomendacoes(usuario.id, []);
    await carregarRecomendacoes();
  });

  document.querySelector(".botao-cancelar").addEventListener("click", () => {
    window.location.href = "../../index.html";
  });

  await carregarRecomendacoes();
});