import { useEffect, useState } from "react";
import axios from "axios";
import { Link } from "react-router-dom";
import { API_URL } from "../config";

export default function AdminSolicitudes() {
  const [solicitudes, setSolicitudes] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState("");

  const fetchSolicitudes = async () => {
    try {
      const token = localStorage.getItem("token");
      const res = await axios.get(`${API_URL}/solicitudes`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      setSolicitudes(res.data);
    } catch (err) {
      setError("Error al cargar las solicitudes");
    } finally {
      setCargando(false);
    }
  };

  useEffect(() => { fetchSolicitudes(); }, []);

  const actualizarEstado = async (id, estado) => {
    const token = localStorage.getItem("token");
    await axios.put(`${API_URL}/solicitudes/${id}`, { estado }, {
      headers: { Authorization: `Bearer ${token}` }
    });
    fetchSolicitudes();
  };

  if (cargando) return <p style={{ textAlign: "center" }}>Cargando...</p>;
  if (error) return <p style={{ color: "red", textAlign: "center" }}>{error}</p>;

  return (
    <div style={{ maxWidth: 900, margin: "40px auto", textAlign: "center" }}>
      <h2>Panel de Administración</h2>
      <Link to="/solicitudes"><button>Volver</button></Link>
      <table border="1" cellPadding="8" style={{ margin: "20px auto" }}>
        <thead><tr><th>ID</th><th>Usuario ID</th><th>Inicio</th><th>Fin</th><th>Motivo</th><th>Estado</th><th>Acciones</th></tr></thead>
        <tbody>
          {solicitudes.map(s => (
            <tr key={s.id}>
              <td>{s.id}</td><td>{s.usuario_id}</td><td>{s.fecha_inicio}</td><td>{s.fecha_fin}</td><td>{s.motivo}</td><td>{s.estado}</td>
              <td>
                {s.estado === "pendiente" ? (
                  <>
                    <button onClick={() => actualizarEstado(s.id, "aprobada")}>Aprobar</button>{" "}
                    <button onClick={() => actualizarEstado(s.id, "rechazada")}>Rechazar</button>
                  </>
                ) : (<b>{s.estado}</b>)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
