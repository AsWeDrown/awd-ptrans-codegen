package gg.aswedrown.ptranscodegen;

public final class Convert {

    private Convert() {}

    public static String snakeToCamel(String snake) {
        StringBuilder camel = new StringBuilder();
        char[] snakeChars = snake.toCharArray();
        boolean nextUpperCase = true;

        for (char ch : snakeChars) {
            if (ch == '_')
                nextUpperCase = true;
            else if (nextUpperCase) {
                camel.append(Character.toUpperCase(ch));
                nextUpperCase = false;
            } else
                camel.append(ch);
        }

        return camel.toString();
    }

}
