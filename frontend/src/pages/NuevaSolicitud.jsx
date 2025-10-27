import { useState } from "react";
import axios from "axios";
import { API_URL } from "../config";

export default function NuevaSolicitud() {
  const [fechaInicio, setFechaInicio] = useState("");
  const [fechaFin, setFechaFin] = useState("");
  const [motivo, setMotivo] = useState("");
  const [mensaje, setMensaje] = useState("");

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      const token = localStorage.getItem("token");
      await axios.post(`${API_URL}/solicitudes`,
        { fecha_inicio: fechaInicio, fecha_fin: fechaFin, motivo },
        { headers: { Authorization: `Bearer ${token}` } }
      );
      setMensaje("✅ Solicitud registrada");
      setFechaInicio(""); setFechaFin(""); setMotivo("");
    } catch (e) {
      setMensaje("❌ Error al registrar");
    }
  };

  return (
    <div style={{ maxWidth: 400, margin: "40px auto", textAlign: "center" }}>
      <h2>Nueva Solicitud</h2>
      <form onSubmit={handleSubmit}>
        <input type="date" value={fechaInicio} onChange={e => setFechaInicio(e.target.value)} required /><br/>
        <input type="date" value={fechaFin} onChange={e => setFechaFin(e.target.value)} required /><br/>
        <textarea placeholder="Motivo" value={motivo} onChange={e => setMotivo(e.target.value)} required></textarea><br/>
        <button type="submit">Enviar</button>
      </form>
      {mensaje && <p>{mensaje}</p>}
    </div>
  );
}
