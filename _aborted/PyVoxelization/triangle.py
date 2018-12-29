# -*- coding: UTF-8 -*-

import numpy as np


def getPlane(p1, p2, p3):
    """计算平面方程"""

    # These two vectors are in the plane
    v1 = p3 - p1
    v2 = p2 - p1

    # the cross product is a vector normal to the plane
    cp = np.cross(v1, v2)
    a, b, c = cp

    # This evaluates a * x3 + b * y3 + c * z3 which equals d
    d = np.dot(cp, p3)

    params = [a, b, c, d]
    maxv = np.max(params)

    return params / maxv


def getDistance(coord, plane):
    """计算点面距离"""

    # [0,0,0,1] * [a,b,c,d]
    # np.dot 意思是 1*a + 0*b + 0*c + 1*d
    d = np.abs(np.dot(coord, plane)) / (np.sqrt(
        plane[0]*plane[0]
        + plane[1]*plane[0]
        + plane[2]*plane[2]
    ))
    return d
