package com.jzqs.app.packageplan.service;

import java.util.Map;

public interface PackageGrantService {
    Map<String, Object> grantPackage(long customerId, String packageCode, int totalMeals, String operatorName);
}
