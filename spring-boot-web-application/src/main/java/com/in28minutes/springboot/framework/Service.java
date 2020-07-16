package com.in28minutes.springboot.framework;

import com.in28minutes.springboot.service.StudentService;

/**
 * サービス
 */
public interface Service {

    /**
     * @return StudentService
     */
    StudentService getStudentService();

}
