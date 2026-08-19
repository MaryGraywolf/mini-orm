package br.com.framework.orm.spi;

public interface OrmLogger {

    void info(String origin, String code, String msg, Object... params);

    void warn(String origin, String code, String msg, Object... params);

    void error(String origin, String code, String msg, Exception e, Object... params);

}
