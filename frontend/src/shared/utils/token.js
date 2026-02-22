export function tokenPreview(token) {
  if (!token) return '-';
  return token.length > 20 ? `${token.slice(0, 20)}...` : token;
}
