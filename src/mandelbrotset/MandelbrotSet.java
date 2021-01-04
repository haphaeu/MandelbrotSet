/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mandelbrotset;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import static java.lang.Math.min;
import static java.lang.Math.max;
import static java.lang.Math.abs;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


/**
 *
 * @author raf
 */
public class MandelbrotSet implements KeyListener, 
                                MouseListener,
                                MouseMotionListener, 
                                MouseWheelListener,
                                ComponentListener,
                                Runnable {
    
    MyDrawPanel panel;
    
    int numThreads;
    int pixelPerThread;
    BlockingQueue<Integer> queue;
    static ArrayList<Thread> threads;
    
    int xres;
    int yres;
    int maxIters;
    
    // Domain discretised
    double[] xdomain;
    double[] ydomain;
    
    // Number of iteractions for each x and y pixel
    int[][] iters;
    
    // Image to show the Mandelbrot set
    BufferedImage img;
    
    // Domain limits
    double xmax, xmin, ymax, ymin;
    LinkedList<Double> xmin_list;
    LinkedList<Double> xmax_list;
    LinkedList<Double> ymin_list;
    LinkedList<Double> ymax_list;
    
    public MandelbrotSet() {
        numThreads = 64;
        
        xres = 1920;
        yres = 1080;
        
        pixelPerThread = 10;
        queue = new LinkedBlockingQueue<>();
        threads = new ArrayList<>();
        
        maxIters = 128;
        
        xmin = -2.0;
        xmax = 1.0;
        ymin = -1.0;
        ymax = 1.0;
        
        xmin_list = new LinkedList<>();
        xmax_list = new LinkedList<>();
        ymin_list = new LinkedList<>();
        ymax_list = new LinkedList<>();
        
        img = new BufferedImage(xres, yres, BufferedImage.TYPE_INT_RGB);
        
        int i;
        for(i = 0; i < numThreads; i++) {
            Thread thread = new Thread(this);
            thread.setName("Thread" + i);
            threads.add(thread);
        }
    }

    
    public static void main(String[] args) {
        System.out.println("main()");
        if (args.length > 1) {
            System.out.println("  args:");
            for(String s: args)
                System.out.println("  " + s);
        }
        MandelbrotSet mandelbrotSet = new MandelbrotSet();
        
        if (args.length > 0) 
            mandelbrotSet.numThreads = Integer.parseInt(args[0]);
        
        mandelbrotSet.setup();
    }

    public void setup() {
        System.out.println("setup()");
        JFrame frame = new JFrame("Mandelbrot Set");
        panel = new MyDrawPanel();
        frame.getContentPane().add(panel);
        frame.addKeyListener(this);
        frame.addMouseListener(this);
        frame.addMouseMotionListener(this);
        frame.addMouseWheelListener(this);
        frame.addComponentListener(this);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(xres, yres);
        frame.setResizable(true);
        frame.setVisible(true);
        
        loop();
    }
    
    public void loop() {
        int proc_time;
        int timer = 30; // ms
        long t0;
        
        try {
        
            Thread.sleep(100);

            // Start all threads
            threads.forEach((thread) -> {thread.start();});

            //threads.forEach((thread) -> {try {
            //    thread.join();
            //    } catch (InterruptedException ex) {}
            //});

            while ( true ) {
                t0 = System.nanoTime();

                panel.repaint();
                proc_time = (int)((System.nanoTime() - t0) / 1e6);

                Thread.sleep(timer - Math.min(timer, proc_time));
            }
        } catch (InterruptedException ex) { }
    }
    
    public void update() {
        updateDomain();
        try { 
            mandelThreaded();
        } catch (InterruptedException ex) {}
        //panel.repaint();
    }
    
    long et_updateDomain;
    public void updateDomain() {
        System.out.println("updateDomain()");
        long t0 = System.nanoTime();
        xdomain = new double[xres];
        ydomain = new double[yres];
        iters = new int[xres][yres];
                
        double xdelta = (xmax - xmin) / xres;
        double ydelta = (ymax- ymin) / yres;
        
        for (int i=0; i < xres; i++)
            xdomain[i] = xmin + i * xdelta;
        
        for (int i=0; i < yres; i++)
            ydomain[i] = ymin + i * ydelta;
        
        et_updateDomain = System.nanoTime() - t0;
    }
    
    long et_mandel;
    public void mandelThreaded() throws InterruptedException {
        System.out.println("mandelThreaded()");
        long t0 = System.nanoTime();
        
        int i = 0;       
        while (i < xres) {
            queue.put(i);
            i += pixelPerThread;
        }
            
        et_mandel = System.nanoTime() - t0;
    }
    
    
    public void mandel(int pixel_start, int pixel_end) {
        //System.out.println("mandel(...)");
        int c;
        double x0, y0, x1, y1, xtmp;
        
        for (int i=pixel_start; i < pixel_end; i++) {
            x0 = xdomain[i];
            for (int j=0; j < yres; j++) {
                y0 = ydomain[j];
                x1 = 0.0;
                y1 = 0.0;
                c = 0;
                while (x1*x1 + y1*y1 <= 4 && c < maxIters) {
                    xtmp = x1*x1 - y1*y1 + x0;
                    y1 = 2*x1*y1 + y0;
                    x1 = xtmp;
                    c++;
                }
                iters[i][j] = c;
            }
                
        }
    }
    
    public void updateImage(int pixel_start, int pixel_end) {
        //System.out.println("updateImage(...)");
        int c, rgb;
        float tmp = 0.0f;
        
        for (int i=pixel_start; i < pixel_end; i++) {
            for (int j=0; j < yres; j++) {
                c = iters[i][j];
                if (c == maxIters)
                    img.setRGB(i, j, 0);
                else {
                    //rgb = Color.HSBtoRGB(c/256f, 1, c/(c+8f));
                    rgb = Color.HSBtoRGB((float)c/maxIters, 1, c/(c+8f));
                    img.setRGB(i, j, rgb);
                }
                
                //r = 255 * (maxIters - c) / maxIters;
                //g = 120;
                //b = 255 * c / maxIters;   
                //int rgb = (r & 0xff) << 16 | (g & 0xff) << 8 | (b & 0xff);
                //img.setRGB(i, j, rgb);
            }
        }
    }
    
    public double[] mouse2domain(int mouseX, int mouseY) {
        double[]xy = new double[2];
        xy[0] = (double) mouseX / xres * (xmax - xmin) + xmin;
        xy[1] = (double) mouseY / yres * (ymax - ymin) + ymin;
        return xy;
    }
    
    // ***************************************************************************************
    // INTERFACES
    // ***************************************************************************************

    // Runnable
    @Override public void run() {
        System.out.println(Thread.currentThread().getName() + " is running");
        int pixel_start, pixel_end;
        try {
            
            //while ( ! queue.isEmpty()) {
            while ( true ) {
                pixel_start = queue.take();
                pixel_end = min(xres, pixel_start + pixelPerThread);
                //System.out.println(Thread.currentThread().getName() + " " + pixel_start + " " + pixel_end);
                mandel(pixel_start, pixel_end);
                updateImage(pixel_start, pixel_end);
            }

        } catch (InterruptedException ex) {}
    }
    
    
    // KeyListener
    @Override public void keyTyped(KeyEvent e) {}
    @Override public void keyPressed(KeyEvent e) {
        System.out.print("Key pressed: ");
        
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:
                System.out.println("Up");
                maxIters *= 2;
                update();
                break;
            case KeyEvent.VK_DOWN:
                System.out.println("Down");
                maxIters /= 4;
                if (maxIters < 1)
                    maxIters = 1;
                update();
            case KeyEvent.VK_B:
                System.out.println("Back");
                if ( ! xmin_list.isEmpty() ) {
                    xmin = xmin_list.pop();
                    xmax = xmax_list.pop();
                    ymin = ymin_list.pop();
                    ymax = ymax_list.pop();
                    update();
                }
                break;
            case KeyEvent.VK_R:
                System.out.println("Repaint");
                panel.repaint();
                break;
        }
    }
    @Override public void keyReleased(KeyEvent e) {}
    
    // MouseListener, MouseMotionListener, MouseWheelListener,

    int mouseStartX, mouseStartY, mouseEndX, mouseEndY, 
        mouseRectX, mouseRectY, mouseRectWidth, mouseRectHeight,
        mouseNowAtX=0, mouseNowAtY=0;
    double[] selectedStartDomain, selectedEndDomain, nowAtDomain;
    
    @Override public void mouseClicked(MouseEvent e) {
        System.out.println("mouseClicked()");
    }
    @Override public void mousePressed(MouseEvent e) {
        System.out.println("mousePressed()");
        mouseStartX = e.getX();
        mouseStartY = e.getY() - 28;
        
        selectedStartDomain = mouse2domain(mouseStartX, mouseStartY);
    }
    @Override public void mouseReleased(MouseEvent e) {
        System.out.println("mouseReleased()");
        
        if (selectedStartDomain != null && selectedEndDomain != null) {
            double x1, x2, y1, y2;
            x1 = selectedStartDomain[0];
            y1 = selectedStartDomain[1];
            x2 = selectedEndDomain[0];
            y2 = selectedEndDomain[1];

            xmin_list.push(xmin);
            xmax_list.push(xmax);
            ymin_list.push(ymin);
            ymax_list.push(ymax);

            xmin = min(x1, x2);
            xmax = max(x1, x2);
            ymin = min(y1, y2);
            ymax = max(y1, y2);

            update();
            
            selectedStartDomain = null;
            selectedEndDomain = null;
            mouseRectX = 0;
            mouseRectY = 0;
            mouseRectWidth = 0;
            mouseRectHeight = 0;
        }
        
        
    }
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
    @Override public void mouseDragged(MouseEvent e) {
        mouseEndX = e.getX();
        mouseEndY = e.getY() - 28;
        mouseRectX = min(mouseStartX, mouseEndX);
        mouseRectY = min(mouseStartY, mouseEndY);
        mouseRectWidth = abs(mouseStartX - mouseEndX);
        mouseRectHeight = abs(mouseStartY - mouseEndY);
        
        selectedEndDomain = mouse2domain(mouseEndX, mouseEndY);
        
        //panel.repaint();
    }
    @Override public void mouseMoved(MouseEvent e) {
        mouseNowAtX = e.getX();
        mouseNowAtY = e.getY() - 27;
        
        nowAtDomain = mouse2domain(mouseNowAtX, mouseNowAtY);
        //panel.repaint();
    }
    @Override public void mouseWheelMoved(MouseWheelEvent e) {}
    
    // ComponentListener
    @Override public void componentMoved(ComponentEvent e) {};
    @Override public void componentShown(ComponentEvent e) {};
    @Override public void componentHidden(ComponentEvent e) {};
    @Override public void componentResized(ComponentEvent e) { 
        System.out.println("componentResized()");
        yres = panel.getHeight();
        xres = panel.getWidth();
        update();
    }
    
    
    // ***************************************************************************************
    // Drawings
    // ***************************************************************************************
    
    class MyDrawPanel extends JPanel {
        
        long repaintTime;
        
        @Override
        public void paintComponent(Graphics gfx) {
            long t0 = System.nanoTime();
            int w = this.getWidth();
            int h = this.getHeight();
            

            gfx.fillRect(0, 0, w, h);

            gfx.drawImage(img, 0, 0, this);

            
            gfx.setColor(Color.white);
            gfx.drawRect(mouseRectX, mouseRectY, mouseRectWidth, mouseRectHeight);
            
            repaintTime = System.nanoTime() - t0;
            
            // Drawing some text at bottom left corner
            
            int y = h, d = 15;
            gfx.drawString(String.format("Mouse at %d, %d", mouseNowAtX, mouseNowAtY), 10, y-=d);
            gfx.drawString(String.format("mandel  %5.1fms", et_mandel/1e6), 10, y-=d);
            gfx.drawString(String.format("updateDomain  %5.1fms", et_updateDomain/1e6), 10, y-=d);
            gfx.drawString(String.format("repaint %5.1fms", repaintTime/1e6), 10, y-=d);
            
            y -= 2 * d;
            
            if (selectedStartDomain != null && selectedEndDomain != null) {
                gfx.drawString(String.format("Selected: x from %.2g to %.2g, y from %.2g to %.2g", 
                        selectedStartDomain[0], selectedEndDomain[0],
                        selectedStartDomain[1], selectedEndDomain[1]), 10, y-=d);
            }
            gfx.drawString(String.format("Iters: %d / %d", iters[mouseNowAtX][mouseNowAtY], 
                    maxIters), 10, y-=d);
            if (nowAtDomain != null)
                gfx.drawString(String.format("Not at: %.2g, %.2g", nowAtDomain[0], 
                            nowAtDomain[1]), 10, y-=d);
            gfx.drawString(String.format("Domain: x from %.2g to %.2g, y from %.2g to %.2g", 
                    xmin, xmax, ymin, ymax), 10, y-=d);
            
            gfx.drawString("Queue size: " + queue.size(), 10, y-=d);
                
            
        }
    }
    
}
