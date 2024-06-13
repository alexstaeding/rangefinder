export function isValidBody<T extends Record<string, unknown>>(
  body: any,
  fields: (keyof T)[],
): T | undefined {
  return fields.every(key => key in body)
    ? body as T
    : undefined
}
