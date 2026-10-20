export const digits = (value: string) => value.replace(/\D/g, "");
export const money = (value: number) =>
  new Intl.NumberFormat("en-PH", { style: "currency", currency: "PHP" }).format(
    value,
  );
