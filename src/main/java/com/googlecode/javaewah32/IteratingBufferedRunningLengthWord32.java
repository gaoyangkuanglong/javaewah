package com.googlecode.javaewah32;



/*
 * Copyright 2009-2013, Daniel Lemire, Cliff Moon, David McIntosh, Robert Becho, Google Inc. and Veronika Zenz
 * Licensed under APL 2.0.
 */
/**
 * Mostly for internal use. Similar to BufferedRunningLengthWord32, but automatically
 * advances to the next BufferedRunningLengthWord32 as words are discarded.
 *
 * @since 0.5.0
 * @author Daniel Lemire and David McIntosh
 */
public final class IteratingBufferedRunningLengthWord32 {
  /**
   * Instantiates a new iterating buffered running length word.
   *
   * @param iterator iterator
   */
  public IteratingBufferedRunningLengthWord32(final EWAHIterator32 iterator) {
    this.iterator = iterator;
    this.brlw = new BufferedRunningLengthWord32(this.iterator.next());
    this.literalWordStartPosition = this.iterator.literalWords() + this.brlw.literalwordoffset;
    this.buffer = this.iterator.buffer();
  }
  

  /**
   * Instantiates a new iterating buffered running length word.
   *
   * @param iterator iterator
   */  
  public IteratingBufferedRunningLengthWord32(final EWAHCompressedBitmap32 bitmap) {
    this(EWAHIterator32.getEWAHIterator(bitmap));
  }
  

  /**
   * Discard first words, iterating to the next running length word if needed.
   *
   * @param x the x
   */
  public void discardFirstWords(int x) {
    
    while (x > 0) {
      if (this.brlw.RunningLength > x) {
        this.brlw.RunningLength -= x;
        return;
      }
      x -= this.brlw.RunningLength;
      this.brlw.RunningLength = 0;
      int toDiscard = x > this.brlw.NumberOfLiteralWords ? this.brlw.NumberOfLiteralWords : x;
    
      this.literalWordStartPosition += toDiscard;
      this.brlw.NumberOfLiteralWords -= toDiscard;
      x -= toDiscard;
      if ((x > 0) || (this.brlw.size() == 0)) {
        if (!this.iterator.hasNext()) {
          break;
        }
        this.brlw.reset(this.iterator.next());
        this.literalWordStartPosition = this.iterator.literalWords(); // + this.brlw.literalwordoffset == 0;
      }
    }
  }
  /**
   * Write out up to max words, returns how many were written
   * @param container target for writes
   * @param max maximal number of writes
   * @return how many written
   */
  public int discharge(BitmapStorage32 container, int max) {
    int index = 0;
    while ((index < max) && (size() > 0)) {
      // first run
      int pl = getRunningLength();
      if (index + pl > max) {
        pl = max - index;
      }
      container.addStreamOfEmptyWords(getRunningBit(), pl);
      index += pl;
      int pd = getNumberOfLiteralWords();
      if (pd + index > max) {
        pd = max - index;
      }
      writeLiteralWords(pd, container);
      discardFirstWords(pl+pd);
      index += pd;
    }
    return index;
  }

  /**
   * Write out up to max words (negated), returns how many were written
   * @param container target for writes
   * @param max maximal number of writes
   * @return how many written
   */
  public int dischargeNegated(BitmapStorage32 container, int max) {
    int index = 0;
    while ((index < max) && (size() > 0)) {
      // first run
      int pl = getRunningLength();
      if (index + pl > max) {
        pl = max - index;
      }
      container.addStreamOfEmptyWords(!getRunningBit(), pl);
      index += pl;
      int pd = getNumberOfLiteralWords();
      if (pd + index > max) {
        pd = max - index;
      }
      writeNegatedLiteralWords(pd, container);
      discardFirstWords(pl+pd);
      index += pd;
    }
    return index;
  }


  /**
   * Write out the remain words, transforming them to zeroes.
   * @param container target for writes
   */
  public void dischargeAsEmpty(BitmapStorage32 container) {
    while(size()>0) {
      container.addStreamOfEmptyWords(false, size());
      discardFirstWords(size());
    }
  }
  
  /**
   * Write out the remaining words
   * @param container target for writes
   */
  public void discharge(BitmapStorage32 container) {
    // fix the offset
    this.brlw.literalwordoffset = this.literalWordStartPosition - this.iterator.literalWords();
    discharge(this.brlw, this.iterator, container);
  }

  /**
   * Get the nth literal word for the current running length word 
   * @param index zero based index
   * @return the literal word
   */
  public int getLiteralWordAt(int index) {
    return this.buffer[this.literalWordStartPosition + index];
  }

  /**
   * Gets the number of literal words for the current running length word.
   *
   * @return the number of literal words
   */
  public int getNumberOfLiteralWords() {
    return this.brlw.NumberOfLiteralWords;
  }

  /**
   * Gets the running bit.
   *
   * @return the running bit
   */
  public boolean getRunningBit() {
    return this.brlw.RunningBit;
  }
  
  /**
   * Gets the running length.
   *
   * @return the running length
   */
  public int getRunningLength() {
    return this.brlw.RunningLength;
  }
  
  /**
   * Size in uncompressed words of the current running length word.
   *
   * @return the int
   */
  public int size() {
    return this.brlw.size();
  }
  
  /**
   * write the first N literal words to the target bitmap.  Does not discard the words or perform iteration.
   * @param numWords
   * @param container
   */
  public void writeLiteralWords(int numWords, BitmapStorage32 container) {
    container.addStreamOfLiteralWords(this.buffer, this.literalWordStartPosition, numWords);
  }
  

  /**
   * write the first N literal words (negated) to the target bitmap.  Does not discard the words or perform iteration.
   * @param numWords
   * @param container
   */
  public void writeNegatedLiteralWords(int numWords, BitmapStorage32 container) {
    container.addStreamOfNegatedLiteralWords(this.buffer, this.literalWordStartPosition, numWords);
  }
  

  /**
   * For internal use. (One could use the non-static dischard method instead,
   * but we expect them to be slower.)
   * 
   * @param initialWord
   *          the initial word
   * @param iterator
   *          the iterator
   * @param container
   *          the container
   */
  protected static void discharge(
    final BufferedRunningLengthWord32 initialWord,
    final EWAHIterator32 iterator, final BitmapStorage32 container) {
    BufferedRunningLengthWord32 runningLengthWord = initialWord;
    for (;;) {
      final int runningLength = runningLengthWord.getRunningLength();
      container.addStreamOfEmptyWords(runningLengthWord.getRunningBit(),
        runningLength);
      container.addStreamOfLiteralWords(iterator.buffer(), iterator.literalWords()
        + runningLengthWord.literalwordoffset,
        runningLengthWord.getNumberOfLiteralWords());
      if (!iterator.hasNext())
        break;
      runningLengthWord = new BufferedRunningLengthWord32(iterator.next());
    }
  }
  private BufferedRunningLengthWord32 brlw;
  private int[] buffer;
  private int literalWordStartPosition;
  private EWAHIterator32 iterator;
}
