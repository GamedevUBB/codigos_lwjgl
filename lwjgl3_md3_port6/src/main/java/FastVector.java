/*
 * Author: Ernest J. Friedman-Hill (modified by Thomas Hourdel).
 * E-mail: thomas.hourdel@libertysurf.fr
 */

/** A fast little vector class. */
public final class FastVector {

	private Object m_v[];  /** Object array. **/
	private int m_ptr = 0;  /** Array pointer (= size). **/

	/** FastVector constructor. */
	public FastVector()	{}


	/** Get the vector size.
	 *  @return Vector size (number of elements).
	 */
	public final int size()	{
		return m_ptr;
	}

	/** Add an element to the vector.
	 *  @param val The element to be added.
	 */
	public final void addElement(Object val) {
		if(m_v == null)
			m_v = new Object[3];
		if(m_ptr >= m_v.length) {
			Object[] nv = new Object[m_v.length * 2];
			System.arraycopy(m_v, 0, nv, 0, m_v.length);
			m_v = nv;
		}
		m_v[m_ptr++] = val;
	}

	/** Get a vector element.
	 *  @param i Element index.
	 *  @return The element wanted.
	 */
	public final Object elementAt(int i) {
		return m_v[i];
	}

	/** Remove an element from the vector.
	 *  @param i The element index to be removed.
	 */
	public final void removeElementAt(int i) {
		m_v[i] = m_v[--m_ptr];
	}
}