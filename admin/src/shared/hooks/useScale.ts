import { useState, useEffect } from 'react';

interface ScaleStyle {
  transform: string;
  transformOrigin: string;
  width: string;
  height: string;
}

export function useScale(targetWidth = 1440, minWidth = 768) {
  const [scaleStyle, setScaleStyle] = useState<ScaleStyle | undefined>(undefined);

  useEffect(() => {
    const handleResize = () => {
      const clientWidth = window.innerWidth;
      // 如果屏幕极小（手机端），放弃等比例缩放，交回给原生媒体查询瀑布流
      // 或者如果屏幕比基准宽度还大，也不需要放大
      if (clientWidth <= minWidth || clientWidth >= targetWidth) {
        setScaleStyle(undefined);
        return;
      }
      
      const scale = clientWidth / targetWidth;
      setScaleStyle({
        transform: `scale(${scale})`,
        transformOrigin: 'left top',
        width: `${100 / scale}%`,
        height: `${100 / scale}%`
      });
    };

    handleResize();
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [targetWidth, minWidth]);

  return scaleStyle;
}
