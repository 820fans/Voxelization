# -*- coding: UTF-8 -*-

import codecs
import xml.etree.ElementTree as ET
import numpy as np
from triangle import *

foepn = codecs.open('source/triangle.amf', 'r', 'utf-8')

tree = ET.parse(foepn)
root = tree.getroot()
mesh = root.find('object').find('mesh')
vertices = mesh.find('vertices')
volume = mesh.find('volume')

vertexVals = []
volumeIndexes = []
volumePlanes = []

for element in vertices.iter('coordinates'):
    x = element.find('x').text
    y = element.find('y').text
    z = element.find('z').text
    vertexVals.append(np.array([float(x), float(y), float(z)]))

for element in volume.iter('triangle'):
    v1 = int(element.find('v1').text)
    v2 = int(element.find('v2').text)
    v3 = int(element.find('v3').text)
    volumeIndexes.append(np.array([v1, v2, v3]))
    volumePlanes.append(getPlane(vertexVals[v1],
                                 vertexVals[v2],
                                 vertexVals[v3]))

# 至此获取到了所有坐标点和三角形下标
# 和所有三角形的平面方程
# 获取距离的函数
getDistance([0, 0, 0, 1], volumePlanes[2])
