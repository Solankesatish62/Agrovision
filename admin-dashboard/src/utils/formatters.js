export const formatTimestamp = (ts) => {
  if (!ts) return 0;
  if (ts.toMillis) return ts.toMillis();
  return ts;
};
