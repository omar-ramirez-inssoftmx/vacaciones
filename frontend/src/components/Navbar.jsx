import { Link } from "react-router-dom";

export default function Navbar() {
  const rol = localStorage.getItem("rol");
  const token = localStorage.getItem("token");
  if (!token) return null;
  return (
    <nav style={{ background: "#f0f0f0", padding: 10, textAlign: "center" }}>
      <Link to="/solicitudes">Mis Solicitudes</Link> |{" "}
      <Link to="/nueva">Nueva Solicitud</Link>
      {rol === "admin" && <>
        {" "} | <Link to="/admin">Panel Admin</Link> | <Link to="/usuarios">Usuarios</Link>
      </>}
      {" "} | <button onClick={() => { localStorage.clear(); window.location.href = "/"; }}>Cerrar sesión</button>
    </nav>
  );
}
