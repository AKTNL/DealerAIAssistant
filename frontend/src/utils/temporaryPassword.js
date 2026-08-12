const LOWERCASE = "abcdefghijkmnopqrstuvwxyz";
const UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ";
const DIGITS = "23456789";
const SYMBOLS = "!@#$%*-_";
const PASSWORD_LENGTH = 18;
const ALL_CHARACTERS = `${LOWERCASE}${UPPERCASE}${DIGITS}${SYMBOLS}`;

export function createTemporaryPassword() {
  if (!globalThis.crypto?.getRandomValues) {
    throw new Error("Secure random generation is unavailable.");
  }

  const characters = [
    randomCharacter(LOWERCASE),
    randomCharacter(UPPERCASE),
    randomCharacter(DIGITS),
    randomCharacter(SYMBOLS)
  ];

  while (characters.length < PASSWORD_LENGTH) {
    characters.push(randomCharacter(ALL_CHARACTERS));
  }

  for (let index = characters.length - 1; index > 0; index -= 1) {
    const target = randomNumber(index + 1);
    [characters[index], characters[target]] = [characters[target], characters[index]];
  }
  return characters.join("");
}

function randomCharacter(characters) {
  return characters[randomNumber(characters.length)];
}

function randomNumber(maximum) {
  const values = new Uint32Array(1);
  globalThis.crypto.getRandomValues(values);
  return values[0] % maximum;
}
