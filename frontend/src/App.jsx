import { Routes, Route, Navigate } from "react-router-dom";
import Login from "./pages/Login";
import MisSolicitudes from "./pages/MisSolicitudes";
import NuevaSolicitud from "./pages/NuevaSolicitud";
import AdminSolicitudes from "./pages/AdminSolicitudes";
import AdminUsuarios from "./pages/AdminUsuarios";
import Navbar from "./components/Navbar";

export default function App() {
  const token = localStorage.getItem("token");
  return (
    <>
      <Navbar />
      <Routes>
        <Route path="/" element={token ? <Navigate to="/solicitudes" /> : <Login />} />
        <Route path="/solicitudes" element={<MisSolicitudes />} />
        <Route path="/nueva" element={<NuevaSolicitud />} />
        <Route path="/admin" element={<AdminSolicitudes />} />
        <Route path="/usuarios" element={<AdminUsuarios />} />
      </Routes>
    </>
  );
}
