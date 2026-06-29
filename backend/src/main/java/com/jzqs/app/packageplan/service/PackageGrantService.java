package com.jzqs.app.packageplan.service;

import com.jzqs.app.packageplan.api.GrantPackageResponse;

public interface PackageGrantService {
    GrantPackageResponse grantPackage(long customerId, String packageCode, int totalMeals, String operatorName);
}
