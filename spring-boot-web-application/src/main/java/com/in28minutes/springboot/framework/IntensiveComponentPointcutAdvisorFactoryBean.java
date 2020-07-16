package com.in28minutes.springboot.framework;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * 集約コンポーネントの PointcutAdvisor を生成する FactoryBean
 *
 * @param <T> 集約コンポーネントインターフェースの型
 */
public class IntensiveComponentPointcutAdvisorFactoryBean<T>
    extends AbstractFactoryBean<StaticMethodMatcherPointcutAdvisor> {

    /** 集約コンポーネントインターフェース */
    private @Nullable Class<T> intensiveComponentType;


    //<editor-fold desc="getter, setter">
    /**
     * @return 集約コンポーネントインターフェース
     */
    public @Nullable Class<T> getIntensiveComponentType() {
        return intensiveComponentType;
    }

    /**
     * @param intensiveComponentType 集約コンポーネントインターフェース
     */
    public void setIntensiveComponentType(Class<T> intensiveComponentType) {
        this.intensiveComponentType = intensiveComponentType;
    }

    @Override
    public Class<StaticMethodMatcherPointcutAdvisor> getObjectType() {
        return StaticMethodMatcherPointcutAdvisor.class;
    }
    //</editor-fold>


    @Override
    protected @NonNull StaticMethodMatcherPointcutAdvisor createInstance() throws Exception {
        Assert.notNull(intensiveComponentType, "IntensiveComponentType must not be null.");
        Assert.isTrue(intensiveComponentType.isInterface(), "IntensiveComponentType must be an interface.");
        Assert.isInstanceOf(
            AutowireCapableBeanFactory.class, getBeanFactory(),
            "BeanFactory must be an AutowireCapableBeanFactory");

        return createAdvisor(
            (AutowireCapableBeanFactory)getBeanFactory(),
            getTargetMethodSet(intensiveComponentType));
    }

    /**
     * 集約コンポーネントインターフェースから対象メソッドの集合を抽出する
     *
     * @param objectType 集約コンポーネントインターフェース
     * @return 対象メソッドの集合
     */
    protected Set<Method> getTargetMethodSet(Class<T> objectType) {
        Set<Method> methods = new HashSet<>();

        ReflectionUtils.doWithMethods(
            objectType,
            methods::add,
            method -> !ReflectionUtils.isObjectMethod((method)) && method.getParameterCount() == 0);

        return Collections.unmodifiableSet(methods);
    }

    /**
     * 対象メソッドが呼ばれたら対応する bean を返す PointcutAdvisor を生成する
     *
     * @param beanFactory BeanFactory
     * @param methodSet 対象メソッドの集合
     * @return PointcutAdvisor
     */
    protected StaticMethodMatcherPointcutAdvisor createAdvisor(AutowireCapableBeanFactory beanFactory, Set<Method> methodSet) {
        StaticMethodMatcherPointcutAdvisor advisor = createMethodMatcher(methodSet);
        advisor.setAdvice(new BeanResolveAdvice(beanFactory, methodSet));
        return advisor;
    }

    /**
     * methodSet かどうかを判定する Pointcut を生成する
     *
     * @param methodSet 対象メソッドの集合
     * @return 生成した Pointcut (Advice は設定されない)
     */
    protected StaticMethodMatcherPointcutAdvisor createMethodMatcher(Set<Method> methodSet) {
        return new StaticMethodMatcherPointcutAdvisor() {
            @Override
            public boolean matches(Method method, Class<?> targetClass) {
                return methodSet.contains(method);
            }
        };
    }


    /**
     * あるインターフェースのメソッドから BeanFactory の対応する bean を解決する Interceptor
     */
    static class BeanResolveAdvice implements Advice, MethodInterceptor {

        /** BeanFactory */
        private final AutowireCapableBeanFactory beanFactory;
        /** 対象メソッドの集合 */
        private final Set<Method> methodSet;

        /** DependencyDescriptor のキャッシュ */
        private final ConcurrentMap<Method, DependencyDescriptor> descriptorCache;

        /**
         * コンストラクター
         *
         * @param beanFactory Bean factory
         * @param methodSet 対象メソッドの集合
         */
        public BeanResolveAdvice(AutowireCapableBeanFactory beanFactory, Set<Method> methodSet)
        {
            this.beanFactory = beanFactory;
            this.methodSet = methodSet;
            this.descriptorCache = new ConcurrentHashMap<>(methodSet.size());
        }

        /**
         * DependencyDescriptor を解決する
         *
         * @param method メソッド
         * @return DependencyDescriptor
         */
        protected DependencyDescriptor resolveDescriptor(Method method) {
            return descriptorCache.computeIfAbsent(method, m -> {
                MethodParameter parameter = new MethodParameter(m, -1);
                return new DependencyDescriptor(parameter, true);
            });
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            return beanFactory.resolveDependency(
                resolveDescriptor(invocation.getMethod()), null);
        }
    }
}
