import { useEffect, useState } from "react";
import axios from "axios";
import { Link } from "react-router-dom";
import { API_URL } from "../config";

export default function MisSolicitudes() {
  const [solicitudes, setSolicitudes] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const fetchData = async () => {
      try {
        const token = localStorage.getItem("token");
        const res = await axios.get(`${API_URL}/solicitudes`, {
          headers: { Authorization: `Bearer ${token}` }
        });
        setSolicitudes(res.data);
      } catch (e) {
        setError("Error al cargar las solicitudes");
      } finally {
        setCargando(false);
      }
    };
    fetchData();
  }, []);

  if (cargando) return <p style={{ textAlign: "center" }}>Cargando...</p>;
  if (error) return <p style={{ color: "red", textAlign: "center" }}>{error}</p>;

  return (
    <div style={{ maxWidth: 800, margin: "40px auto", textAlign: "center" }}>
      <h2>Mis Solicitudes</h2>
      <Link to="/nueva"><button>+ Nueva Solicitud</button></Link>
      {solicitudes.length === 0 ? (
        <p>No tienes solicitudes</p>
      ) : (
        <table border="1" cellPadding="8" style={{ margin: "0 auto" }}>
          <thead><tr><th>ID</th><th>Inicio</th><th>Fin</th><th>Motivo</th><th>Estado</th></tr></thead>
          <tbody>
            {solicitudes.map(s => (
              <tr key={s.id}>
                <td>{s.id}</td><td>{s.fecha_inicio}</td><td>{s.fecha_fin}</td><td>{s.motivo}</td><td>{s.estado}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
