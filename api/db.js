import pkg from "pg";
const { Pool } = pkg;

// Vercel leerá esta variable desde el entorno
const pool = new Pool({
  connectionString: process.env.POSTGRES_URL,
  ssl: { rejectUnauthorized: false },
});

// Verificar conexión
(async () => {
  try {
    const client = await pool.connect();
    console.log("✅ Conectado a PostgreSQL");
    client.release();
  } catch (error) {
    console.error("❌ Error al conectar con PostgreSQL:", error);
  }
})();

export default pool;
