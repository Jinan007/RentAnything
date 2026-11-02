import "./globals.css";

export const metadata = {
  title: "Arogya-Setu",
  description: "Zero-app hospital queue and audio prescriptions",
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body className="bg-slate-50 text-slate-900 antialiased">{children}</body>
    </html>
  );
}
