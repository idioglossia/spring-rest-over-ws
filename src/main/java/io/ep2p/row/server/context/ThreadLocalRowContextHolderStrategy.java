package io.ep2p.row.server.context;

import org.springframework.util.Assert;

public class ThreadLocalRowContextHolderStrategy implements RowContextHolderStrategy {
    private static final ThreadLocal<RowContext> contextHolder = new ThreadLocal<>();

    @Override
    public void clearContext() {
        contextHolder.remove();
    }

    @Override
    public RowContext getContext() {
        RowContext ctx = contextHolder.get();
        if (ctx == null) {
            ctx = this.createEmptyContext();
            contextHolder.set(ctx);
        }

        return ctx;
    }

    @Override
    public void setContext(RowContext rowContext) {
        Assert.notNull(rowContext, "Only non-null Row context instances are permitted");
        contextHolder.set(rowContext);
    }

    @Override
    public RowContext createEmptyContext() {
        return new DefaultContextImpl(new RowUser(), false);
    }
}
